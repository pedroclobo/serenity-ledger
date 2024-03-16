package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CommitQuorumMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.service.models.Pair;
import pt.ulisboa.tecnico.hdsledger.service.models.RoundValueClientSignature;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig.ByzantineBehavior;
import pt.ulisboa.tecnico.hdsledger.utilities.exceptions.InvalidSignatureException;

public class NodeService implements UDPService {

  private final HDSLogger logger;
  // Nodes configurations
  private final ProcessConfig[] nodesConfig;
  // Client configurations
  private final ProcessConfig[] clientsConfig;

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
  private final int TIMEOUT = 1000;
  // Keep track of already started consensus instances
  private final Set<Integer> setupConsensus = ConcurrentHashMap.newKeySet();
  // Synchronize threads when waiting for a new consensus instance
  private final Object waitingConsensusLock = new Object();

  // Ledger (for now, just a list of strings)
  private ArrayList<String> ledger = new ArrayList<String>();

  // Map between client id and public key
  private final Map<Integer, String> clientPublicKeys = new ConcurrentHashMap<>();

  public NodeService(Link link, Link clientLink, ProcessConfig config, ProcessConfig[] nodesConfig,
      ProcessConfig[] clientsConfig, boolean debug) {

    this.logger = new HDSLogger(NodeService.class.getName(), debug);

    this.link = link;
    this.clientLink = clientLink;
    this.config = config;
    this.nodesConfig = nodesConfig;
    this.clientsConfig = clientsConfig;

    // Initialize the client public keys map
    for (ProcessConfig clientConfig : clientsConfig) {
      clientPublicKeys.put(clientConfig.getId(), clientConfig.getPublicKeyPath());
    }

    this.messages = new MessageBucket(nodesConfig.length);

    this.timer = new Timer();
  }

  public ProcessConfig getConfig() {
    return this.config;
  }

  public ArrayList<String> getLedger() {
    return this.ledger;
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

  public ConsensusMessage createConsensusMessage(String value, int instance, int round,
      int clientId, String valueSignature) {
    PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value, clientId, valueSignature);

    ConsensusMessage consensusMessage =
        new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
            .setConsensusInstance(instance).setRound(round).setMessage(prePrepareMessage.toJson())
            .build();

    logger
        .info(MessageFormat.format("[{0}]: Sending PRE-PREPARE with value `{1}` and client id {2}",
            config.getId(), prePrepareMessage.getValue(), clientId));
    return consensusMessage;
  }

  /*
   * Start an instance of consensus for a value.
   *
   * Only the current leader will start a consensus instance. The remaining nodes only update
   * values.
   *
   * @param inputValue Value to value agreed upon
   */
  public void setupConsensus(String value, int consensusInstance, int clientId,
      String valueSignature) {

    if (setupConsensus.contains(consensusInstance)) {
      logger.info(MessageFormat.format("[{0}]: Consensus instance {1} already started",
          config.getId(), currentConsensusInstance.get()));
      return;
    }

    startConsensus(value, clientId, valueSignature);
  }

  public void startConsensus(String value, int clientId, String valueSignature) {
    // Only start a consensus instance if the last one was decided
    // We need to be sure that the previous value has been decided
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

    startConsensus2(value, clientId, valueSignature);
  }

  private synchronized void startConsensus2(String value, int clientId, String valueSignature) {
    // Set initial consensus values
    int localConsensusInstance = this.currentConsensusInstance.incrementAndGet();
    InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance,
        new InstanceInfo(value, clientId, valueSignature));

    // Consensus was already started
    if (existingConsensus != null) {
      logger.info(MessageFormat.format("[{0}]: Consensus instance {1} already started",
          config.getId(), localConsensusInstance));
      return;
    }

    // Leader broadcasts PRE-PREPARE message
    InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
    if (this.config.isLeader(localConsensusInstance, instance.getCurrentRound())
        || this.config.getByzantineBehavior() == ProcessConfig.ByzantineBehavior.FakeLeader) {
      logger
          .info(MessageFormat.format("[{0}]: I'm the leader, sending PRE-PREPARE", config.getId()));
      this.link.broadcast(this.createConsensusMessage(value, localConsensusInstance,
          instance.getCurrentRound(), instance.getClientId(), instance.getValueSignature()));
    } else {
      logger.info(MessageFormat.format("[{0}]: I'm not the leader, waiting for PRE-PREPARE",
          config.getId()));
    }

    setupConsensus.add(localConsensusInstance);

    restartTimer();
  }

  /*
   * Handle pre prepare messages.
   *
   * If the message came from the leader and is justified, broadcast prepare.
   *
   * @param message Message to be handled
   */
  public synchronized void uponPrePrepare(ConsensusMessage message) {
    PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

    int consensusInstance = message.getConsensusInstance();
    int round = message.getRound();
    int senderId = message.getSenderId();
    int senderMessageId = message.getMessageId();
    String value = prePrepareMessage.getValue();
    int clientId = prePrepareMessage.getClientId();
    String valueSignature = prePrepareMessage.getValueSignature();

    // Check if the value is signed by the client
    try {
      if (!prePrepareMessage.verifyValueSignature(this.clientPublicKeys.get(clientId), value)) {
        logger
            .info(MessageFormat.format("[{0}]: Invalid signature for value `{1}` and client id {2}",
                config.getId(), value, clientId));
        return;
      }
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      throw new HDSSException(ErrorMessage.SignatureVerificationError);
    }

    if (!instanceInfo.containsKey(consensusInstance)) {
      startConsensus(value, clientId, valueSignature);
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

    // Justify pre-prepare
    if (!(isLeader(senderId) && consensusInstance == currentConsensusInstance.get()
        && round == instanceInfo.get(consensusInstance).getCurrentRound()
        && messages.justifyPrePrepare(consensusInstance, round, value))) {
      logger.info(MessageFormat.format("[{0}]: Unjustified PRE-PREPARE for (λ, r) = ({1}, {2})",
          config.getId(), consensusInstance, round));
      return;
    }

    if (instanceInfo.get(consensusInstance).triggeredPrePrepareRule(round)) {
      logger.info(MessageFormat.format(
          "[{0}]: Already triggered pre-prepare rule for (λ, r) = ({1}, {2}), ignoring",
          config.getId(), consensusInstance, round));
      return;
    }

    logger.info(MessageFormat.format(
        "[{0}]: Received PRE-PREPARE message from {1} for (λ, r) = ({2}, {3}) with value `{4}` and client id {5}",
        config.getId(), senderId, consensusInstance, round, value, clientId));

    // Set instance value
    if (!setupConsensus.contains(consensusInstance)) {
      setupConsensus(value, consensusInstance, clientId, valueSignature);
    }

    InstanceInfo instance = this.instanceInfo.get(consensusInstance);
    instance.setTriggeredPrePrepareRule(round);

    restartTimer();

    PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getValue(), clientId,
        prePrepareMessage.getValueSignature());

    // Byzantine behavior
    if (config.getByzantineBehavior() == ByzantineBehavior.FakeValue) {
      prepareMessage =
          new PrepareMessage("wrong value", clientId, prePrepareMessage.getValueSignature());
    }

    ConsensusMessage consensusMessage =
        new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE)
            .setConsensusInstance(consensusInstance).setRound(round)
            .setMessage(prepareMessage.toJson()).setReplyTo(senderId)
            .setReplyToMessageId(senderMessageId).build();

    this.link.broadcast(consensusMessage);

    logger.info(MessageFormat.format(
        "[{0}]: Broadcasting PREPARE message for (λ, r) = ({1}, {2}) with value `{3}` and client id {4}",
        config.getId(), consensusInstance, round, value, clientId));
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
    String value = prepareMessage.getValue();
    int clientId = prepareMessage.getClientId();
    String valueSignature = prepareMessage.getValueSignature();

    // Check if the value was signed by the client
    try {
      if (!prepareMessage.verifyValueSignature(this.clientPublicKeys.get(clientId), value)) {
        logger
            .info(MessageFormat.format("[{0}]: Invalid signature for value `{1}` and client id {2}",
                config.getId(), value, clientId));
        return;
      }
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      throw new HDSSException(ErrorMessage.SignatureVerificationError);
    }

    // Discard messages from others (λ, r)
    if (consensusInstance != currentConsensusInstance.get()
        || round != instanceInfo.get(consensusInstance).getCurrentRound()) {
      logger.info(MessageFormat.format(
          "[{0}]: Received PREPARE message for (λ, r) = ({1}, {2}) != current (λ, r) = ({3}, {4}), ignoring",
          config.getId(), consensusInstance, round, currentConsensusInstance.get(),
          instanceInfo.get(currentConsensusInstance.get()).getCurrentRound()));
      return;
    }

    logger.info(MessageFormat.format(
        "[{0}]: Received PREPARE from {1}, (λ, r) = ({2}, {3}) with value `{4}` and client id {5}",
        config.getId(), senderId, consensusInstance, round, value, clientId));

    // Doesn't add duplicate messages
    messages.addMessage(message);

    // Set instance values
    this.instanceInfo.putIfAbsent(consensusInstance,
        new InstanceInfo(value, clientId, valueSignature));
    InstanceInfo instance = this.instanceInfo.get(consensusInstance);

    if (messages.hasPrepareQuorum(consensusInstance, round, value)) {
      logger.info(MessageFormat.format(
          "[{0}]: Received valid PREPARE quorum for (λ, r) = ({1}, {2}) with value `{3}` and client id {4}",
          config.getId(), consensusInstance, round, value, clientId));

      instance.setPreparedValue(value);
      instance.setPreparedRound(round);
      instance.setPreparedClientId(clientId);
      instance.setPreparedValueSignature(valueSignature);
      instance.setTriggeredPrepareRule(round);

      // Get the prepare messages to reply with a commit message
      Collection<ConsensusMessage> sendersMessage =
          messages.getMessages(consensusInstance, round).values();

      // Broadcast COMMIT
      CommitMessage c = new CommitMessage(value, clientId, valueSignature);
      instance.setCommitMessage(c);

      sendersMessage.forEach(senderMessage -> {
        ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
            .setConsensusInstance(consensusInstance).setRound(round).setMessage(c.toJson()).build();

        if (config.getByzantineBehavior() == ByzantineBehavior.FakeValue) {
          m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
              .setConsensusInstance(consensusInstance).setRound(round)
              .setMessage(new CommitMessage("wrong value", clientId, valueSignature).toJson())
              .build();
        }

        logger.info(MessageFormat.format(
            "[{0}]: Broadcasting COMMIT message for (λ, r) = ({1}, {2}) with value `{3}` and client id",
            config.getId(), consensusInstance, round, value, clientId));

        link.broadcast(m);
      });
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
    String value = commitMessage.getValue();
    int clientId = commitMessage.getClientId();

    InstanceInfo instance = this.instanceInfo.get(consensusInstance);

    // Within an instance of the algorithm, each upon rule is triggered at most once
    // for any round r
    if (instance.getCommittedRound().isPresent()) {
      logger.info(MessageFormat.format(
          "[{0}]: Already received COMMIT message for (λ, r) = ({1}, {2}), ignoring",
          config.getId(), consensusInstance, round));
      return;
    }

    logger.info(MessageFormat.format(
        "[{0}]: Received COMMIT message from {1} for (λ, r) = ({2}, {3}) with value `{4}` and client id {5}",
        config.getId(), senderId, consensusInstance, round, value, clientId));

    try {
      if (!commitMessage.verifyValueSignature(this.clientPublicKeys.get(clientId), value)) {
        logger
            .info(MessageFormat.format("[{0}]: Invalid signature for value `{1}` and client id {2}",
                config.getId(), value, clientId));
        return;
      }
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      throw new HDSSException(ErrorMessage.SignatureVerificationError);
    }

    messages.addMessage(message);

    Pair<Boolean, Optional<Set<CommitMessage>>> a = messages.hasCommitQuorum(consensusInstance);

    Boolean hasQuorum = a.getFirst();

    if (hasQuorum) {

      logger.info(MessageFormat.format(
          "[{0}]: Received valid COMMIT quorum for (λ, r) = ({1}, {2}) with value `{3}` and client id {4}",
          config.getId(), consensusInstance, round, value, clientId));

      Set<CommitMessage> commitQuorum = a.getSecond().get();

      stopTimer();

      instance = this.instanceInfo.get(consensusInstance);
      instance.setCommittedRound(round);
      instance.setCommitQuorum(commitQuorum);

      value = commitQuorum.iterator().next().getValue();

      decide(consensusInstance, round, value);

      // Notify clients
      Message messageToClient = new AppendMessage(config.getId(), Message.Type.APPEND, value);
      clientLink.broadcast(messageToClient);
    } else {
      logger.info(
          MessageFormat.format("[{0}]: No valid COMMIT QUORUM for (λ, r) = ({1}, {2}), ignoring",
              config.getId(), consensusInstance, round));
    }
  }

  private synchronized void decide(int consensusInstance, int round, String value) {
    synchronized (ledger) {

      // Increment size of ledger to accommodate current instance
      ledger.ensureCapacity(consensusInstance);
      while (ledger.size() < consensusInstance - 1) {
        ledger.add("");
      }

      ledger.add(consensusInstance - 1, value);

      logger.info(MessageFormat.format("[{0}]: Current Ledger: {1}", config.getId(),
          String.join("", ledger)));
    }

    lastDecidedConsensusInstance.getAndIncrement();

    logger.info(MessageFormat.format("[{0}]: Decided {1} on (λ, r) = ({2}, {3})", config.getId(),
        value, consensusInstance, round));

    // Notify waiting threads
    synchronized (waitingConsensusLock) {
      waitingConsensusLock.notifyAll();
    }
  }

  public synchronized void startRoundChange() {
    int consensusInstance = this.currentConsensusInstance.get();
    int round = instanceInfo.get(consensusInstance).getCurrentRound() + 1;

    synchronized (instanceInfo.get(consensusInstance)) {
      InstanceInfo instance = instanceInfo.get(consensusInstance);
      instance.setCurrentRound(round);

      logger.info(MessageFormat.format("[{0}]: Setting local round to {1} on λ = {2}",
          config.getId(), round, consensusInstance));

      restartTimer();

      RoundChangeMessage roundChangeMessage =
          new RoundChangeMessage(instance.getPreparedRound(), instance.getPreparedValue(),
              instance.getPreparedClientId(), instance.getPreparedValueSignature());

      // TODO: is not instance.getValueSignature() and instance.getClientId() if prepared value is
      // available
      ConsensusMessage consensusMessage =
          new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
              .setConsensusInstance(consensusInstance).setRound(round)
              .setMessage(roundChangeMessage.toJson()).build();

      this.link.broadcast(consensusMessage);
    }
  }

  // TODO: do we trust this message?
  public synchronized void uponRoundChange(ConsensusMessage message) {
    int consensusInstance = message.getConsensusInstance();
    int round = message.getRound();
    int senderId = message.getSenderId();

    if (consensusInstance <= lastDecidedConsensusInstance.get()) {
      logger.info(MessageFormat.format(
          "[{0}]: Received ROUND_CHANGE message for old λ = {1}, sending COMMIT_QUORUM message to {2}",
          config.getId(), consensusInstance, senderId));

      CommitQuorumMessage commitQuorumMessage =
          new CommitQuorumMessage(instanceInfo.get(consensusInstance).getCommitQuorum().get());

      ConsensusMessage consensusMessage =
          new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT_QUORUM)
              .setConsensusInstance(consensusInstance).setRound(round)
              .setMessage(commitQuorumMessage.toJson()).build();

      this.link.send(senderId, consensusMessage);

      return;
    }

    logger.info(MessageFormat.format(
        "[{0}]: Received ROUND_CHANGE message from {1} for (λ, r) = ({2}, {3})", config.getId(),
        message.getSenderId(), consensusInstance, round));

    messages.addMessage(message);

    Pair<Boolean, Optional<RoundValueClientSignature>> a =
        messages.hasRoundChangeQuorum(consensusInstance, round);

    boolean validRoundChangeQuorum = a.getFirst();

    if ((this.config.isLeader(consensusInstance, round)
        || this.config.getByzantineBehavior() == ByzantineBehavior.FakeLeader)
        && validRoundChangeQuorum
        && round == instanceInfo.get(consensusInstance).getCurrentRound()) {

      InstanceInfo instance = instanceInfo.get(consensusInstance);
      int highestPreparedRound = instance.getCurrentRound();
      String highestPreparedValue = instance.getInputValue();
      int clientId = instance.getClientId();
      String valueSignature = instance.getValueSignature();

      // There is a highest prepared value
      if (a.getSecond().isPresent()) {
        highestPreparedRound = a.getSecond().get().getRound();
        highestPreparedValue = a.getSecond().get().getValue();
        clientId = a.getSecond().get().getClientId();
        valueSignature = a.getSecond().get().getValueSignature();
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

      logger.info(MessageFormat.format("[{0}]: Node is leader, sending PRE-PREPARE message",
          config.getId()));

      this.link.broadcast(this.createConsensusMessage(highestPreparedValue,
          currentConsensusInstance.get(), highestPreparedRound, clientId, valueSignature));
      return;
    }

    Optional<Integer> minRound = messages.hasValidRoundChangeSet(consensusInstance, round);

    if (minRound.isPresent()) {
      InstanceInfo instance = this.instanceInfo.get(consensusInstance);

      if (instance.triggeredRoundChangeSetRule(round)) {
        logger.info(MessageFormat.format(
            "[{0}]: Already triggered round change set rule for (λ, r) = ({1}, {2}), ignoring",
            config.getId(), consensusInstance, round));
        return;
      }

      instance.setTriggeredRoundChangeSetRule(round);

      logger.info(MessageFormat.format("[{0}]: Received valid ROUND_CHANGE set", config.getId()));

      // Set the current round to the minimum round
      synchronized (instance) {
        instance.setCurrentRound(minRound.get());

        restartTimer();

        logger
            .info(MessageFormat.format("[{0}]: Broadcasting ROUND_CHANGE message", config.getId()));

        RoundChangeMessage roundChangeMessage =
            new RoundChangeMessage(instance.getPreparedRound(), instance.getPreparedValue(),
                instance.getPreparedClientId(), instance.getPreparedValueSignature());

        ConsensusMessage consensusMessage =
            new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                .setConsensusInstance(consensusInstance).setRound(round)
                .setMessage(roundChangeMessage.toJson()).build();
        this.link.broadcast(consensusMessage);
      }
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
    long valueCount = quorum.stream().map(CommitMessage::getValue).distinct().count();

    if (valueCount != 1) {
      return false;
    }

    for (CommitMessage commitMessage : quorum) {
      try {
        if (!commitMessage.verifyValueSignature(
            this.clientPublicKeys.get(commitMessage.getClientId()), commitMessage.getValue())) {
          return false;
        }
      } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
        throw new HDSSException(ErrorMessage.SignatureVerificationError);
      }
    }

    return true;
  }

  public synchronized void uponCommitQuorum(ConsensusMessage message) {
    CommitQuorumMessage commitQuorumMessage = message.deserializeCommitQuorumMessage();

    int consensusInstance = message.getConsensusInstance();
    int round = message.getRound();
    String value = commitQuorumMessage.getQuorum().iterator().next().getValue();
    int clientId = commitQuorumMessage.getQuorum().iterator().next().getClientId();

    verifyCommitQuorum(commitQuorumMessage.getQuorum());

    if (consensusInstance <= lastDecidedConsensusInstance.get()) {
      logger.info(MessageFormat.format(
          "[{0}]: Received COMMIT_QUORUM message for already decided λ = {1}, ignoring",
          config.getId(), consensusInstance));
      return;
    }

    logger.info(MessageFormat.format(
        "[{0}]: Received COMMIT_QUORUM for (λ, r) = ({1}, {2}) with value `{3}` and client id {4}",
        config.getId(), consensusInstance, round, value, clientId));

    stopTimer();
    decide(consensusInstance, round, value);
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
