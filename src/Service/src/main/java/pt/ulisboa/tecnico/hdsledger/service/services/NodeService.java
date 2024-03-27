package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.application.BalanceRequest;
import pt.ulisboa.tecnico.hdsledger.communication.application.BalanceResponse;
import pt.ulisboa.tecnico.hdsledger.communication.application.ClientRequest;
import pt.ulisboa.tecnico.hdsledger.communication.application.ClientResponse;
import pt.ulisboa.tecnico.hdsledger.communication.application.TransferRequest;
import pt.ulisboa.tecnico.hdsledger.communication.application.TransferResponse;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.CommitQuorumMessage;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.models.Account;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.Ledger;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.service.models.Pair;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSException;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig.ByzantineBehavior;
import pt.ulisboa.tecnico.hdsledger.utilities.exceptions.InvalidSignatureException;

public class NodeService implements UDPService {

  private final HDSLogger logger;
  // Nodes configurations
  private final ProcessConfig[] nodesConfig;

  // Current node is leader
  private final ProcessConfig config;

  // Link to communicate with nodes
  private final Link link;
  // Link to communicate with clients
  private final Link clientLink;

  private final MessageBucket messages;

  // Consensus instance information per consensus instance
  private final Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
  // Current consensus instance
  private final AtomicInteger currentConsensusInstance = new AtomicInteger(0);
  // Last decided consensus instance
  private final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);
  // Timer
  private Timer timer;
  private final int TIMEOUT = 2000;
  // Keep track of already started consensus instances
  private final Set<Integer> setupConsensus = ConcurrentHashMap.newKeySet();
  // Synchronize threads when waiting for a new consensus instance
  private final Object waitingConsensusLock = new Object();

  // Track nonce for each client
  private final Map<Integer, Set<Integer>> clientNonces = new ConcurrentHashMap<>();

  // Ledger
  private Ledger ledger;

  // Map between client id and public key
  private final Map<Integer, String> clientPublicKeys = new ConcurrentHashMap<>();

  public NodeService(Link link, Link clientLink, ProcessConfig config, ProcessConfig[] nodesConfig,
      ProcessConfig[] clientsConfig, boolean debug) {

    this.logger = new HDSLogger(NodeService.class.getName(), debug);

    this.link = link;
    this.clientLink = clientLink;
    this.config = config;
    this.nodesConfig = nodesConfig;

    // Initialize the client public keys map
    for (ProcessConfig clientConfig : clientsConfig) {
      clientPublicKeys.put(clientConfig.getId(), clientConfig.getPublicKeyPath());
    }

    this.messages = new MessageBucket(nodesConfig.length);

    this.timer = new Timer();

    this.ledger = new Ledger(nodesConfig, clientsConfig);

    for (ProcessConfig clientConfig : clientsConfig) {
      clientNonces.put(clientConfig.getId(), ConcurrentHashMap.newKeySet());
    }
  }

  public ProcessConfig getConfig() {
    return this.config;
  }

  public Ledger getLedger() {
    return ledger;
  }

  private boolean isLeader(int id) {
    int consensusInstance = this.currentConsensusInstance.get();
    int round = instanceInfo.get(consensusInstance).getCurrentRound();

    return nodesConfig[id - 1].isLeader(consensusInstance, round);
  }

  private void stopTimer() {
    timer.cancel();
    timer = new Timer();
  }

  private void restartTimer() {
    timer.cancel();
    timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        startRoundChange();
        logger.info(
            MessageFormat.format("[{0}]: Timer fired, starting round change", config.getId()));
      }
    }, TIMEOUT);
  }

  /*
   * Start an instance of consensus for a value.
   *
   * Only the current leader will start a consensus instance. The remaining nodes only update
   * values.
   *
   * @param inputValue Value to value agreed upon
   */
  public void setupConsensus(int consensusInstance, Block inputBlock) {
    if (setupConsensus.contains(consensusInstance)) {
      logger.info(MessageFormat.format("[{0}]: Consensus instance {1} already started",
          config.getId(), currentConsensusInstance.get()));
      return;
    }

    waitAndStartConsensus(inputBlock);
  }

  /*
   * Wait for the last consensus instance to be decided and start a new one
   */
  public void waitAndStartConsensus(Block inputBlock) {
    // Only start a consensus instance if the last one was decided
    synchronized (waitingConsensusLock) {
      while (lastDecidedConsensusInstance.get() < currentConsensusInstance.get()) {
        logger.info(MessageFormat.format("[{0}]: Waiting for λ={1} to be decided", config.getId(),
            currentConsensusInstance.get()));
        try {
          waitingConsensusLock.wait();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }

    startConsensus(inputBlock);
  }

  /*
   * Start a consensus instance for a value
   *
   * 1. Initialize the consensus instance 2. If the node is a leader, broadcast a PRE-PREPARE
   * message 3. Start the round change timer
   */
  private synchronized void startConsensus(Block block) {
    // Set initial consensus values
    int localConsensusInstance = this.currentConsensusInstance.incrementAndGet();
    InstanceInfo existingConsensus =
        this.instanceInfo.put(localConsensusInstance, new InstanceInfo(block));

    // Consensus was already started
    if (existingConsensus != null) {
      logger.info(MessageFormat.format("[{0}]: Consensus instance {1} already started",
          config.getId(), localConsensusInstance));
      return;
    }

    // Leader broadcasts PRE-PREPARE message
    InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);

    PrePrepareMessage prePrepareMessage = new PrePrepareMessage(block.toJson());
    ConsensusMessage message = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
        .setConsensusInstance(localConsensusInstance).setRound(instance.getCurrentRound())
        .setMessage(prePrepareMessage.toJson()).build();

    if (this.config.isLeader(localConsensusInstance, instance.getCurrentRound())) {
      logger.info(
          MessageFormat.format("[{0}]: I'm the leader, sending PRE-PREPARE for (λ, r) = ({1}, {2})",
              config.getId(), localConsensusInstance, instance.getCurrentRound()));
      this.link.broadcast(message);

      // Testing: Fake leader sends PRE-PREPARE
    } else if (this.config.getByzantineBehavior() == ProcessConfig.ByzantineBehavior.FakeLeader) {
      logger.info(MessageFormat.format(
          "[{0}]: I'm the (fake) leader, sending PRE-PREPARE for (λ, r) = ({1}, {2})",
          config.getId(), localConsensusInstance, instance.getCurrentRound()));
      this.link.broadcast(message);

    } else {
      logger.info(MessageFormat.format("[{0}]: I'm not the leader, waiting for PRE-PREPARE",
          config.getId()));
    }

    setupConsensus.add(localConsensusInstance);

    // Start round change timer
    restartTimer();
  }

  /*
   * Check message validity, check pre-prepare justification If message is justified, restart timer
   * and broadcast prepare
   */
  public synchronized void uponPrePrepare(ConsensusMessage message) {
    PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

    int consensusInstance = message.getConsensusInstance();
    int round = message.getRound();
    int senderId = message.getSenderId();
    int senderMessageId = message.getMessageId();
    String block = prePrepareMessage.getBlock();

    // Set instance value if missing
    if (!setupConsensus.contains(consensusInstance)) {
      setupConsensus(consensusInstance, Block.fromJson(block));
    }

    // Discard messages from others (λ, r)
    if (consensusInstance != currentConsensusInstance.get()
        || round != instanceInfo.get(consensusInstance).getCurrentRound()) {
      logger.info(MessageFormat.format(
          "[{0}]: Received PRE-PREPARE message for (λ, r) = ({1}, {2}) != current (λ, r) = ({3}, {4}), ignoring",
          config.getId(), consensusInstance, round, currentConsensusInstance.get(),
          instanceInfo.get(currentConsensusInstance.get()).getCurrentRound()));
      return;
    }

    // Don't trigger the pre-prepare rule more than once per round
    if (instanceInfo.get(consensusInstance).triggeredPrePrepareRule(round)) {
      logger.info(MessageFormat.format(
          "[{0}]: Already triggered pre-prepare rule for (λ, r) = ({1}, {2}), ignoring",
          config.getId(), consensusInstance, round));
      return;
    }

    // Justify pre-prepare
    if (!(isLeader(senderId) && messages.justifyPrePrepare(consensusInstance, round))) {
      logger.info(MessageFormat.format("[{0}]: Unjustified PRE-PREPARE for (λ, r) = ({1}, {2})",
          config.getId(), consensusInstance, round));
      return;
    }

    logger.info(MessageFormat.format(
        "[{0}]: Received valid PRE-PREPARE message from {1} for (λ, r) = ({2}, {3})",
        config.getId(), senderId, consensusInstance, round));

    InstanceInfo instance = this.instanceInfo.get(consensusInstance);
    instance.setTriggeredPrePrepareRule(round);

    // Restart timer
    restartTimer();

    // Broadcast PREPARE
    PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getBlock());

    // Testing: Send fake value
    // if (config.getByzantineBehavior() == ByzantineBehavior.FakeValue) {
    // prepareMessage =
    // new PrepareMessage("wrong value", clientId, prePrepareMessage.getValueSignature());
    // }

    ConsensusMessage consensusMessage =
        new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE)
            .setConsensusInstance(consensusInstance).setRound(round)
            .setMessage(prepareMessage.toJson()).setReplyTo(senderId)
            .setReplyToMessageId(senderMessageId).build();

    this.link.broadcast(consensusMessage);

    logger.info(MessageFormat.format("[{0}]: Broadcasting PREPARE message for (λ, r) = ({1}, {2})",
        config.getId(), consensusInstance, round));
  }

  /*
   * Handle prepare messages and if there is a valid quorum broadcast commit.
   *
   * @param message Message to be handled
   */
  public synchronized void uponPrepare(ConsensusMessage message) {
    PrepareMessage prepareMessage = message.deserializePrepareMessage();
    int senderId = message.getSenderId();

    int consensusInstance = message.getConsensusInstance();
    int round = message.getRound();
    String block = prepareMessage.getBlock();

    // Set instance value if missing
    if (!setupConsensus.contains(consensusInstance)) {
      setupConsensus(consensusInstance, Block.fromJson(block));
    }
    InstanceInfo instance = this.instanceInfo.get(consensusInstance);

    // Discard messages from others (λ, r)
    if (consensusInstance != currentConsensusInstance.get()
        || round != instanceInfo.get(consensusInstance).getCurrentRound()) {
      logger.info(MessageFormat.format(
          "[{0}]: Received PREPARE message for (λ, r) = ({1}, {2}) != current (λ, r) = ({3}, {4}), ignoring",
          config.getId(), consensusInstance, round, currentConsensusInstance.get(),
          instanceInfo.get(currentConsensusInstance.get()).getCurrentRound()));
      return;
    }

    // Only trigger the prepare rule once per round
    if (instance.triggeredPrepareQuorumRule(round)) {
      logger.info(MessageFormat.format(
          "[{0}]: Already triggered prepare rule for (λ, r) = ({1}, {2}), ignoring", config.getId(),
          consensusInstance, round));
      return;
    }

    logger.info(MessageFormat.format("[{0}]: Received PREPARE from {1}, (λ, r) = ({2}, {3})",
        config.getId(), senderId, consensusInstance, round));

    // Doesn't add duplicate messages
    messages.addMessage(message);

    // Valid prepare quorum
    if (messages.hasPrepareQuorum(consensusInstance, round, block)) {
      logger
          .info(MessageFormat.format("[{0}]: Received valid PREPARE quorum for (λ, r) = ({1}, {2})",
              config.getId(), consensusInstance, round));

      instance.setPreparedBlock(Block.fromJson(block));
      instance.setPreparedRound(round);
      instance.setTriggeredPrepareQuorumRule(round);
      instance.setPreparedQuorum(messages.getPrepareQuorum(consensusInstance, round, block));

      // Broadcast COMMIT
      CommitMessage c = new CommitMessage(block);
      instance.setCommitMessage(c);

      ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
          .setConsensusInstance(consensusInstance).setRound(round).setMessage(c.toJson()).build();

      // // Testing: Send fake value
      // if (config.getByzantineBehavior() == ByzantineBehavior.FakeValue) {
      // m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
      // .setConsensusInstance(consensusInstance).setRound(round)
      // .setMessage(new CommitMessage("wrong value", clientId, valueSignature).toJson())
      // .build();
      // }

      logger.info(MessageFormat.format("[{0}]: Broadcasting COMMIT message for (λ, r) = ({1}, {2})",
          config.getId(), consensusInstance, round, block));

      link.broadcast(m);
    }
  }

  /*
   * Handle commit messages and decide if there is a valid quorum
   *
   * @param message Message to be handled
   */
  public synchronized void uponCommit(ConsensusMessage message) {
    CommitMessage commitMessage = message.deserializeCommitMessage();

    int senderId = message.getSenderId();
    int consensusInstance = message.getConsensusInstance();
    int round = message.getRound();
    String block = commitMessage.getBlock();

    InstanceInfo instance = this.instanceInfo.get(consensusInstance);

    // Don't trigger the commit rule more than once per round
    if (instance.triggeredCommitQuorumRule(round)) {
      logger.info(MessageFormat.format(
          "[{0}]: Already received COMMIT message for (λ, r) = ({1}, {2}), ignoring",
          config.getId(), consensusInstance, round));
      return;
    }

    logger.info(
        MessageFormat.format("[{0}]: Received COMMIT message from {1} for (λ, r) = ({2}, {3})",
            config.getId(), senderId, consensusInstance, round));

    messages.addMessage(message);

    // Valid COMMIT quorum
    if (messages.hasCommitQuorum(consensusInstance)) {

      logger
          .info(MessageFormat.format("[{0}]: Received valid COMMIT quorum for (λ, r) = ({1}, {2})",
              config.getId(), consensusInstance, round));

      // Safe to get() as hasCommitQuorum returned true
      Set<CommitMessage> commitQuorum = messages.getCommitQuorum(consensusInstance).get();

      // Update instance committed values
      instance = this.instanceInfo.get(consensusInstance);
      instance.setCommittedRound(round);
      instance.setCommitQuorum(commitQuorum);
      instance.setTriggeredCommitQuorumRule(round);
      block = commitQuorum.iterator().next().getBlock();

      // Stop the timer
      stopTimer();

      // Add the value to the ledger
      decide(consensusInstance, round, Block.fromJson(block));

      // Apply block to the ledger
      applyBlockAndReplyToClients(consensusInstance);

    } else {
      logger.info(
          MessageFormat.format("[{0}]: No valid COMMIT QUORUM for (λ, r) = ({1}, {2}), ignoring",
              config.getId(), consensusInstance, round));
    }
  }

  private void applyBlockAndReplyToClients(int consensusInstance) {
    if (consensusInstance > lastDecidedConsensusInstance.get()) {
      logger.info(MessageFormat.format(
          "[{0}]: Can't apply block for consensus instance {1} that is not decided", config.getId(),
          consensusInstance));
      return;
    }

    Block block = instanceInfo.get(consensusInstance).getDecidedBlock().get();

    for (ClientRequest transaction : block.getTransactions()) {

      switch (transaction.getType()) {

        case BALANCE_REQUEST:
          int clientId = transaction.getSenderId();
          BalanceRequest balanceRequest = transaction.deserializeBalanceMessage();
          int nonce = balanceRequest.getNonce();
          String publicKeyPath = clientPublicKeys.get(clientId);

          boolean valid = true;

          // Check if the nonce is valid
          if (clientNonces.get(clientId).contains(nonce)) {
            valid = false;
          }

          // Check if the signature is valid
          try {
            if (!transaction.verifySignature(publicKeyPath)) {
              valid = false;
            }
          } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException
              | InvalidKeySpecException e) {
            throw new HDSException(ErrorMessage.SignatureVerificationError);
          }

          // Check if account exists
          String publicKeyHash;
          try {
            publicKeyHash = RSACryptography.digest(balanceRequest.getPublicKey().toString());
          } catch (NoSuchAlgorithmException e) {
            throw new HDSException(ErrorMessage.DigestError);
          }
          if (!ledger.hasAccount(publicKeyHash)) {
            valid = false;
          }

          if (!valid) {
            // Send response to client
            BalanceResponse balanceResponse =
                new BalanceResponse(false, balanceRequest.getNonce(), Optional.empty());
            ClientResponse response = new ClientResponse(config.getId(),
                Message.Type.BALANCE_RESPONSE, balanceResponse.toJson());

            clientLink.send(transaction.getSenderId(), response);

          } else {
            // Extract account and balance
            Account account = ledger.getAccount(publicKeyHash);
            int balance = account.getBalance();

            // Register client nonce to avoid replay attacks
            clientNonces.get(clientId).add(nonce);

            // Send response to client
            BalanceResponse balanceResponse =
                new BalanceResponse(true, balanceRequest.getNonce(), Optional.of(balance));
            ClientResponse response = new ClientResponse(config.getId(),
                Message.Type.BALANCE_RESPONSE, balanceResponse.toJson());

            clientLink.send(transaction.getSenderId(), response);
          }

          break;

        case TRANSFER_REQUEST:
          clientId = transaction.getSenderId();
          TransferRequest transferRequest = transaction.deserializeTransferMessage();
          nonce = transferRequest.getNonce();
          publicKeyPath = clientPublicKeys.get(clientId);

          PublicKey sourcePublicKey = transferRequest.getSourcePublicKey();
          PublicKey destinationPublicKey = transferRequest.getDestinationPublicKey();

          valid = true;

          // Check if the nonce is valid
          if (clientNonces.get(clientId).contains(nonce)) {
            valid = false;
          }

          // Check if the signature is valid
          try {
            if (!transaction.verifySignature(publicKeyPath)) {
              valid = false;
            }
          } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException
              | InvalidKeySpecException e) {
            throw new HDSException(ErrorMessage.SignatureVerificationError);
          }

          // Check if source and destination accounts exist
          String sourcePublicKeyHash;
          String destinationPublicKeyHash;
          try {
            sourcePublicKeyHash = RSACryptography.digest(sourcePublicKey.toString());
            destinationPublicKeyHash = RSACryptography.digest(destinationPublicKey.toString());
          } catch (NoSuchAlgorithmException e) {
            throw new HDSException(ErrorMessage.DigestError);
          }
          if (!ledger.hasAccount(sourcePublicKeyHash)
              || !ledger.hasAccount(destinationPublicKeyHash)) {
            valid = false;
          }

          Account sourceAccount = ledger.getAccount(sourcePublicKeyHash);
          Account destinationAccount = ledger.getAccount(destinationPublicKeyHash);

          // Check if the sender's accounts matches the source account
          if (sourceAccount.getOwnerId() != clientId) {
            valid = false;
          }

          // Check that the source account has enough balance
          if (sourceAccount.getBalance() < transferRequest.getAmount()) {
            valid = false;
          }

          if (!valid) {
            // Send response to client
            TransferResponse transferResponse = new TransferResponse(false,
                transferRequest.getNonce(), transferRequest.getSourcePublicKey(),
                transferRequest.getDestinationPublicKey(), transferRequest.getAmount());
            ClientResponse response = new ClientResponse(config.getId(),
                Message.Type.TRANSFER_RESPONSE, transferResponse.toJson());

            clientLink.send(transaction.getSenderId(), response);
          } else {
            // Transfer amount
            int amount = transferRequest.getAmount();
            sourceAccount.subtractBalance(amount);
            destinationAccount.addBalance(amount);

            // Send response to client
            TransferResponse transferResponse = new TransferResponse(true,
                transferRequest.getNonce(), transferRequest.getSourcePublicKey(),
                transferRequest.getDestinationPublicKey(), amount);
            ClientResponse response = new ClientResponse(config.getId(),
                Message.Type.TRANSFER_RESPONSE, transferResponse.toJson());

            clientLink.send(transaction.getSenderId(), response);
          }

          break;

        default:
          throw new UnsupportedOperationException();

      }
    }
  }

  private synchronized void decide(int consensusInstance, int round, Block block) {
    ledger.add(block);

    logger.info(MessageFormat.format("[{0}]: Current Ledger has {1} blocks", config.getId(),
        ledger.getLedger().size()));

    lastDecidedConsensusInstance.set(consensusInstance);
    instanceInfo.get(consensusInstance).setDecidedBlock(block);

    // Notify waiting threads
    synchronized (waitingConsensusLock) {
      waitingConsensusLock.notifyAll();
    }
  }

  public synchronized void startRoundChange() {
    int consensusInstance = this.currentConsensusInstance.get();
    int round = instanceInfo.get(consensusInstance).getCurrentRound() + 1;

    synchronized (instanceInfo.get(consensusInstance)) {
      // Increment current round by 1
      InstanceInfo instance = instanceInfo.get(consensusInstance);
      instance.setCurrentRound(round);

      logger.info(MessageFormat.format("[{0}]: Setting local round to {1} on λ = {2}",
          config.getId(), round, consensusInstance));

      // Restart the timer
      restartTimer();

      // Broadcast round change message
      Optional<Block> preparedBlock = instance.getPreparedBlock();
      Optional<String> preparedBlockJson;
      if (preparedBlock.isPresent()) {
        preparedBlockJson = Optional.of(preparedBlock.get().toJson());
      } else {
        preparedBlockJson = Optional.empty();
      }
      RoundChangeMessage roundChangeMessage = new RoundChangeMessage(instance.getPreparedRound(),
          preparedBlockJson, instance.getPreparedQuorum());

      ConsensusMessage consensusMessage =
          new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
              .setConsensusInstance(consensusInstance).setRound(round)
              .setMessage(roundChangeMessage.toJson()).build();

      this.link.broadcast(consensusMessage);
    }
  }

  /*
   * Validates the prepared quorum that is piggybacked on a round change message
   */
  public synchronized boolean validatePreparedQuorum(Set<ConsensusMessage> preparedQuorum,
      int consensusInstance, int round) {

    // Check that each message has a unique sender
    if (preparedQuorum.stream().map(ConsensusMessage::getSenderId).distinct()
        .count() != preparedQuorum.size()) {
      logger.info(MessageFormat.format(
          "[{0}]: Invalid prepared quorum for (λ, r) = ({1}, {2}), as that are repeated senders, ignoring",
          config.getId(), consensusInstance, round));
      return false;
    }

    // Check that all messages have the same instance
    if (preparedQuorum.stream().map(ConsensusMessage::getConsensusInstance).distinct()
        .count() != 1) {
      logger.info(MessageFormat.format(
          "[{0}]: Invalid prepared quorum for (λ, r) = ({1}, {2}), as that are different λ, ignoring",
          config.getId(), consensusInstance, round));
      return false;
    }

    // Check that all messages have the same prepared round
    if (preparedQuorum.stream().map(ConsensusMessage::getRound).distinct().count() != 1) {
      logger.info(MessageFormat.format(
          "[{0}]: Invalid prepared quorum for (λ, r) = ({1}, {2}), as that are different r, ignoring",
          config.getId(), consensusInstance, round));
      return false;
    }

    // Check that all messages have the same prepared value
    if (preparedQuorum.stream().map(ConsensusMessage::deserializePrepareMessage)
        .map(PrepareMessage::getBlock).distinct().count() != 1) {
      logger.info(MessageFormat.format(
          "[{0}]: Invalid prepared quorum for (λ, r) = ({1}, {2}), as that are different values, ignoring",
          config.getId(), consensusInstance, round));
      return false;
    }

    return true;
  }

  public synchronized void uponRoundChange(ConsensusMessage message) {
    RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();

    int consensusInstance = message.getConsensusInstance();
    int round = message.getRound();
    int senderId = message.getSenderId();
    Optional<Set<ConsensusMessage>> preparedQuorum = roundChangeMessage.getPreparedQuorum();

    // Set instance value if missing
    if (!setupConsensus.contains(consensusInstance)) {
      setupConsensus(consensusInstance, new Block());
    }

    // Received ROUND_CHANGE for an old λ
    // Send commit quorum to sender process
    if (consensusInstance <= lastDecidedConsensusInstance.get()) {
      logger.info(MessageFormat.format(
          "[{0}]: Received ROUND_CHANGE message for old λ = {1}, sending COMMIT_QUORUM message to {2}",
          config.getId(), consensusInstance, senderId));

      // Safe to get() as the instance is already decided
      CommitQuorumMessage commitQuorumMessage =
          new CommitQuorumMessage(instanceInfo.get(consensusInstance).getCommitQuorum().get());

      // Send commit quorum to sender process
      ConsensusMessage consensusMessage =
          new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT_QUORUM)
              .setConsensusInstance(consensusInstance).setRound(round)
              .setMessage(commitQuorumMessage.toJson()).build();

      this.link.send(senderId, consensusMessage);

      return;
    }

    if (consensusInstance != currentConsensusInstance.get()) {
      logger.info(MessageFormat.format(
          "[{0}]: Received ROUND_CHANGE message for (λ, r) = ({1}, {2}) != current (λ, r) = ({3}, {4}), ignoring",
          config.getId(), consensusInstance, round, currentConsensusInstance.get(),
          instanceInfo.get(currentConsensusInstance.get()).getCurrentRound()));
      return;
    }

    logger.info(MessageFormat.format(
        "[{0}]: Received ROUND_CHANGE message from {1} for (λ, r) = ({2}, {3})", config.getId(),
        message.getSenderId(), consensusInstance, round));

    messages.addMessage(message);

    // Valid round change quorum
    if ((this.config.isLeader(consensusInstance, round)
        || this.config.getByzantineBehavior() == ByzantineBehavior.FakeLeader)
        && messages.hasRoundChangeQuorum(consensusInstance, round)
        && round == instanceInfo.get(consensusInstance).getCurrentRound()) {

      // Instance values
      InstanceInfo instance = instanceInfo.get(consensusInstance);
      int highestPreparedRound = instance.getCurrentRound();
      String highestPreparedValue = instance.getInputBlock().toJson();

      // Validate prepared quorum
      if (preparedQuorum.isPresent()
          && !validatePreparedQuorum(preparedQuorum.get(), consensusInstance, round)) {
        logger.info(
            MessageFormat.format("[{0}]: Invalid prepared quorum for (λ, r) = ({1}, {2}), ignoring",
                config.getId(), consensusInstance, round));
        return;
      } else if (preparedQuorum.isPresent()) {
        // Add prepared quorum to messages
        preparedQuorum.get().forEach(m -> messages.addMessage(m));
      }

      // Retrieve highestPrepared, if there is one
      Optional<Pair<Integer, String>> highestPrepared =
          messages.getHighestPrepared(consensusInstance, round);

      // There is a highest prepared value
      // Update values
      if (highestPrepared.isPresent()) {
        highestPreparedRound = highestPrepared.get().getFirst();
        highestPreparedValue = highestPrepared.get().getSecond();
      }

      InstanceInfo instanceInfo = this.instanceInfo.get(currentConsensusInstance.get());

      if (instanceInfo.triggeredRoundChangeQuorumRule(round)) {
        logger.info(MessageFormat.format(
            "[{0}]: Already triggered round change quorum rule for (λ, r) = ({1}, {2}), ignoring",
            config.getId(), consensusInstance, round));
        return;
      }

      instanceInfo.setTriggeredRoundChangeQuorumRule(round);

      logger
          .info(MessageFormat.format("[{0}]: Received valid ROUND_CHANGE quorum", config.getId()));

      logger.info(MessageFormat.format(
          "[{0}]: Node is leader, sending PRE-PREPARE message for (λ, r) = ({1}, {2})",
          config.getId(), consensusInstance, round));

      // Broadcast PRE-PREPARE
      PrePrepareMessage prePrepareMessage = new PrePrepareMessage(highestPreparedValue);
      ConsensusMessage consensusMessage =
          new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
              .setConsensusInstance(currentConsensusInstance.get()).setRound(highestPreparedRound)
              .setMessage(prePrepareMessage.toJson()).build();

      this.link.broadcast(consensusMessage);

      return;
    }

    InstanceInfo instance = this.instanceInfo.get(consensusInstance);

    // Valid round change set
    if (messages.hasValidRoundChangeSet(consensusInstance, instance.getCurrentRound())) {

      if (instance.triggeredRoundChangeSetRule(round)) {
        logger.info(MessageFormat.format(
            "[{0}]: Already triggered round change set rule for (λ, r) = ({1}, {2}), ignoring",
            config.getId(), consensusInstance, round));
        return;
      }

      instance.setTriggeredRoundChangeSetRule(round);

      // Safe to get() as hasValidRoundChangeSet returned true
      int minRound =
          messages.getMinRoundOfRoundChangeSet(consensusInstance, instance.getCurrentRound()).get();

      logger.info(MessageFormat.format("[{0}]: Received valid ROUND_CHANGE set", config.getId()));

      // Set the current round to the minimum round
      synchronized (instance) {
        instance.setCurrentRound(minRound);

        restartTimer();

        logger
            .info(MessageFormat.format("[{0}]: Broadcasting ROUND_CHANGE message", config.getId()));

        // Broadcast ROUND_CHANGE message
        Optional<Block> preparedBlock = instance.getPreparedBlock();
        Optional<String> preparedBlockJson;
        if (preparedBlock.isPresent()) {
          preparedBlockJson = Optional.of(preparedBlock.get().toJson());
        } else {
          preparedBlockJson = Optional.empty();
        }
        RoundChangeMessage newRoundChangeMessage = new RoundChangeMessage(
            instance.getPreparedRound(), preparedBlockJson, instance.getPreparedQuorum());

        ConsensusMessage consensusMessage =
            new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                .setConsensusInstance(consensusInstance).setRound(round)
                .setMessage(newRoundChangeMessage.toJson()).build();

        this.link.broadcast(consensusMessage);
      }
    } else {
      logger.info(
          MessageFormat.format("[{0}]: No valid ROUND_CHANGE set for (λ, r) = ({1}, {2}), ignoring",
              config.getId(), consensusInstance, round));
    }
  }

  // TODO: verify instance, same round, senders
  public synchronized boolean verifyCommitQuorum(Set<CommitMessage> quorum) {
    int n = config.getN();
    int f = (n - 1) / 3;
    int quorumSize = 2 * f + 1;

    if (quorum.size() < quorumSize) {
      return false;
    }

    // All commit messages must have the same value
    long valueCount = quorum.stream().map(CommitMessage::getBlock).distinct().count();

    if (valueCount != 1) {
      return false;
    }

    return true;
  }

  public synchronized void uponCommitQuorum(ConsensusMessage message) {
    CommitQuorumMessage commitQuorumMessage = message.deserializeCommitQuorumMessage();

    int consensusInstance = message.getConsensusInstance();
    int round = message.getRound();
    String block = commitQuorumMessage.getQuorum().iterator().next().getBlock();

    verifyCommitQuorum(commitQuorumMessage.getQuorum());

    if (consensusInstance <= lastDecidedConsensusInstance.get()) {
      logger.info(MessageFormat.format(
          "[{0}]: Received COMMIT_QUORUM message for already decided λ = {1}, ignoring",
          config.getId(), consensusInstance));
      return;
    }

    logger.info(MessageFormat.format("[{0}]: Received COMMIT_QUORUM for (λ, r) = ({1}, {2})",
        config.getId(), consensusInstance, round));

    stopTimer();

    decide(consensusInstance, round, Block.fromJson(block));

    applyBlockAndReplyToClients(consensusInstance);
  }

  @Override
  public void listen() {
    try {
      // Thread to listen on every request
      new Thread(() -> {
        try {
          while (true) {
            Message message;

            try {
              message = link.receive();
            } catch (InvalidSignatureException e) {
              logger.info(MessageFormat.format("[{0}]: EXCEPTION: {1}", this.config.getId(),
                  e.getMessage()));
              continue;
            }

            // Byzantine Tests
            if (config.getByzantineBehavior() == ByzantineBehavior.Drop) {
              logger.info(MessageFormat.format("[{0}]: Dropping message", this.config.getId()));
              continue;
            }

            // Separate thread to handle each message
            new Thread(() -> {

              switch (message.getType()) {

                case PRE_PREPARE -> uponPrePrepare((ConsensusMessage) message);

                case PREPARE -> uponPrepare((ConsensusMessage) message);

                case COMMIT -> uponCommit((ConsensusMessage) message);

                case ROUND_CHANGE -> uponRoundChange((ConsensusMessage) message);

                case COMMIT_QUORUM -> uponCommitQuorum((ConsensusMessage) message);

                case ACK -> {
                }

                case IGNORE -> {
                }

                default -> {
                }

              }

            }).start();
          }
        } catch (SocketException e) {
          // Supress message during shutdown
        } catch (IOException | ClassNotFoundException e) {
          e.printStackTrace();
        }
      }).start();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void shutdown() {
    link.shutdown();
  }

}
