package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.net.SocketException;
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
import pt.ulisboa.tecnico.hdsledger.utilities.HDSLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
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

  // Consensus instance -> Round -> List of prepare messages
  private final MessageBucket prepareMessages;
  // Consensus instance -> Round -> List of commit messages
  private final MessageBucket commitMessages;
  // Consensus instance -> Round -> List of round change messages
  private final MessageBucket roundChangeMessages;

  // Store if already received pre-prepare for a given <consensus, round>
  private final Map<Integer, Map<Integer, Boolean>> receivedPrePrepare = new ConcurrentHashMap<>();
  // Consensus instance information per consensus instance
  private final Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
  // Current consensus instance
  private final AtomicInteger currentConsensusInstance = new AtomicInteger(0);
  // Last decided consensus instance
  private final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);
  // Timer
  private Timer timer;
  private final int TIMEOUT = 1000;
  // Store if already received set of f+1 round change messages for a given <consensus, round>
  private final Map<Integer, Map<Integer, Boolean>> receivedRoundChangeSet =
      new ConcurrentHashMap<>();
  // Store if already received quorum of 2f+1 round change messages for a given <consensus, round>
  private final Map<Integer, Map<Integer, Boolean>> receivedRoundChangeQuorum =
      new ConcurrentHashMap<>();
  // Keep track of already started consensus instances
  private final Set<Integer> setupConsensus = ConcurrentHashMap.newKeySet();
  // Synchronize threads when waiting for a new consensus instance
  private final Object waitingConsensusLock = new Object();

  // Ledger (for now, just a list of strings)
  private ArrayList<String> ledger = new ArrayList<String>();

  public NodeService(Link link, Link clientLink, ProcessConfig config, ProcessConfig[] nodesConfig,
      boolean debug) {

    this.logger = new HDSLogger(NodeService.class.getName(), debug);

    this.link = link;
    this.clientLink = clientLink;
    this.config = config;
    this.nodesConfig = nodesConfig;

    this.prepareMessages = new MessageBucket(nodesConfig.length);
    this.commitMessages = new MessageBucket(nodesConfig.length);
    this.roundChangeMessages = new MessageBucket(nodesConfig.length);

    this.timer = new Timer();
  }

  public ProcessConfig getConfig() {
    return this.config;
  }

  public int getCurrentConsensusInstance() {
    return this.currentConsensusInstance.get();
  }

  public int getCurrentRound() {
    return this.instanceInfo.get(this.currentConsensusInstance.get()).getCurrentRound();
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

  public ConsensusMessage createConsensusMessage(String value, int instance, int round) {
    PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value);

    ConsensusMessage consensusMessage =
        new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
            .setConsensusInstance(instance).setRound(round).setMessage(prePrepareMessage.toJson())
            .build();

    logger.info(MessageFormat.format("[{0}]: Sending PRE-PREPARE with value {1}", config.getId(),
        prePrepareMessage.getValue()));
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
  public void setupConsensus(String value, int consensusInstance) {

    if (setupConsensus.contains(consensusInstance)) {
      logger.info(MessageFormat.format("[{0}]: Consensus instance {1} already started",
          config.getId(), currentConsensusInstance.get()));
      return;
    }

    startConsensus(value);
  }

  public void startConsensus(String value) {
    // Only start a consensus instance if the last one was decided
    // We need to be sure that the previous value has been decided
    synchronized (waitingConsensusLock) {
      while (lastDecidedConsensusInstance.get() < currentConsensusInstance.get()) {
        logger.info(MessageFormat.format("[{0}]: Waiting for consensus instance {1} to be decided",
            config.getId(), currentConsensusInstance.get()));
        try {
          waitingConsensusLock.wait();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }

    // Set initial consensus values
    int localConsensusInstance = this.currentConsensusInstance.incrementAndGet();
    InstanceInfo existingConsensus =
        this.instanceInfo.put(localConsensusInstance, new InstanceInfo(value));

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
      this.link.broadcast(
          this.createConsensusMessage(value, localConsensusInstance, instance.getCurrentRound()));
    } else {
      logger.info(MessageFormat.format("[{0}]: I'm not the leader, waiting for PRE-PREPARE",
          config.getId()));
    }

    setupConsensus.add(localConsensusInstance);

    restartTimer();
  }

  private boolean justifyPrePrepare() {
    int consensusInstance = getCurrentConsensusInstance();
    int round = getCurrentRound();

    Pair<Boolean, Optional<Pair<Integer, String>>> a =
        roundChangeMessages.hasValidRoundChangeQuorum(consensusInstance, round);
    Boolean hasQuorum = a.getFirst();
    Optional<Pair<Integer, String>> highestPrepared = a.getSecond();

    if (!hasQuorum) {
      return round == 1;
    }

    if (highestPrepared.isEmpty()) {
      return true;
    }

    int prj = highestPrepared.get().getFirst();
    String prv = highestPrepared.get().getSecond();

    Optional<String> preparedValue =
        prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance, round);
    if (preparedValue.isEmpty()) {
      return round == 1;
    }

    return round == 1 || (round == prj && preparedValue.get().equals(prv));
  }

  /*
   * Handle pre prepare messages.
   *
   * If the message came from the leader and is justified, broadcast prepare.
   *
   * @param message Message to be handled
   */
  public void uponPrePrepare(ConsensusMessage message) {

    int consensusInstance = message.getConsensusInstance();
    int round = message.getRound();
    int senderId = message.getSenderId();
    int senderMessageId = message.getMessageId();

    PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

    String value = prePrepareMessage.getValue();

    logger.info(MessageFormat.format(
        "[{0}]: Received PRE-PREPARE message from {1} consensus instance {2} round {3} with value {4}",
        config.getId(), senderId, consensusInstance, round, value));

    // Set instance value
    if (!setupConsensus.contains(consensusInstance)) {
      setupConsensus(value, consensusInstance);
    }

    // this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));

    // int consensusInstance = this.currentConsensusInstance.get();
    // int round = instanceInfo.get(consensusInstance).getCurrentRound();

    // return nodesConfig[id - 1].isLeader(consensusInstance, round);

    // Justify pre-prepare
    if (!(isLeader(senderId) && justifyPrePrepare())) {
      logger.info(MessageFormat.format("[{0}]: Unjustified PRE-PREPARE", config.getId()));
      return;
    }

    // Within an instance of the algorithm,
    // each upon rule is triggered at most once for any round r
    receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
    if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) {
      logger.info(MessageFormat.format(
          "[{0}]: Already received PRE-PREPARE for consensus instance {1}, round {2}, ignoring",
          config.getId(), consensusInstance, round));
      return;
    }

    restartTimer();

    PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getValue());

    ConsensusMessage consensusMessage =
        new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE)
            .setConsensusInstance(consensusInstance).setRound(round)
            .setMessage(prepareMessage.toJson()).setReplyTo(senderId)
            .setReplyToMessageId(senderMessageId).build();

    this.link.broadcast(consensusMessage);
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

    logger.info(MessageFormat.format(
        "[{0}]: Received PREPARE from {1}, consensus instance {2}, round {3} with value {4}",
        config.getId(), senderId, consensusInstance, round, value));

    // Doesn't add duplicate messages
    prepareMessages.addMessage(message);

    // Set instance values
    this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));
    InstanceInfo instance = this.instanceInfo.get(consensusInstance);

    // Within an instance of the algorithm,
    // each upon rule is triggered at most once for any round r.
    // Late prepare (consensus already ended for other nodes) only reply to him (as
    // an ACK)
    // TODO: is this necessary? we already have a commit quorum message
    if (instance.getPreparedRound().isPresent() && instance.getPreparedRound().get() >= round) {
      logger.info(MessageFormat.format(
          "[{0}]: Already received PREPARE for consensus instance {1} round {2}, replying again to make sure it reaches the initial sender",
          config.getId(), consensusInstance, round));

      ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
          .setConsensusInstance(consensusInstance).setRound(round).setReplyTo(senderId)
          .setReplyToMessageId(message.getMessageId())
          .setMessage(instance.getCommitMessage().get().toJson()).build();

      link.send(senderId, m);

      return;
    }

    // Find value with valid quorum
    Optional<String> preparedValue =
        prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance, round);

    if (preparedValue.isPresent()) {
      instance.setPreparedValue(preparedValue.get());
      instance.setPreparedRound(round);

      // Get the prepare messages to reply with a commit message
      Collection<ConsensusMessage> sendersMessage =
          prepareMessages.getMessages(consensusInstance, round).values();

      // Broadcast COMMIT
      CommitMessage c = new CommitMessage(preparedValue.get());
      instance.setCommitMessage(c);
      sendersMessage.forEach(senderMessage -> {
        ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
            .setConsensusInstance(consensusInstance).setRound(round)
            .setReplyTo(senderMessage.getSenderId())
            .setReplyToMessageId(senderMessage.getMessageId()).setMessage(c.toJson()).build();

        link.send(senderMessage.getSenderId(), m);
      });
    }
  }

  /*
   * Handle commit messages and decide if there is a valid quorum
   *
   * @param message Message to be handled
   */
  public synchronized void uponCommit(ConsensusMessage message) {
    int senderId = message.getSenderId();
    int consensusInstance = message.getConsensusInstance();
    int round = message.getRound();

    logger.info(MessageFormat.format(
        "[{0}]: Received COMMIT message from {1}: consensus instance {2} round {3}", config.getId(),
        senderId, consensusInstance, round));

    commitMessages.addMessage(message);

    InstanceInfo instance = this.instanceInfo.get(consensusInstance);

    if (instance == null) {
      // Should never happen because only receives commit as a response to a prepare message
      MessageFormat.format(
          "{0} - CRITICAL: Received COMMIT message from {1} consensus instance {2} round {3} BUT NO INSTANCE INFO",
          config.getId(), message.getSenderId(), consensusInstance, round);
      return;
    }

    // Within an instance of the algorithm, each upon rule is triggered at most once
    // for any round r
    if (instance.getCommittedRound().isPresent() && instance.getCommittedRound().get() >= round) {
      logger.info(MessageFormat.format(
          "[{0}]: Already received COMMIT message for consensus instance {1} round {2}, ignoring",
          config.getId(), consensusInstance, round));
      return;
    }

    Pair<Boolean, Optional<Set<CommitMessage>>> a =
        commitMessages.hasValidCommitQuorum(config.getId(), consensusInstance, round);
    Boolean hasQuorum = a.getFirst();

    if (hasQuorum) {

      Set<CommitMessage> commitQuorum = a.getSecond().get();

      stopTimer();

      instance = this.instanceInfo.get(consensusInstance);
      instance.setCommittedRound(round);
      instance.setCommitQuorum(commitQuorum);

      String value = commitQuorum.iterator().next().getValue();

      decide(consensusInstance, round, value);

      // Notify clients
      Message messageToClient = new AppendMessage(config.getId(), Message.Type.APPEND, value);
      clientLink.broadcast(messageToClient);
    }
  }

  private void decide(int consensusInstance, int round, String value) {
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

    logger.info(MessageFormat.format("[{0}]: Decided on consensus instance {1} round {2}",
        config.getId(), consensusInstance, round));

    // Notify waiting threads
    synchronized (waitingConsensusLock) {
      waitingConsensusLock.notifyAll();
    }
  }

  public void startRoundChange() {
    int consensusInstance = this.currentConsensusInstance.get();
    int round = instanceInfo.get(consensusInstance).getCurrentRound() + 1;

    synchronized (instanceInfo.get(consensusInstance)) {
      InstanceInfo instance = instanceInfo.get(consensusInstance);
      instance.setCurrentRound(round);

      logger
          .info(MessageFormat.format("[{0}]: Setting local round to {1} on consensus instance {2}",
              config.getId(), round, consensusInstance));

      restartTimer();

      RoundChangeMessage roundChangeMessage =
          new RoundChangeMessage(instance.getPreparedRound(), instance.getPreparedValue());

      ConsensusMessage consensusMessage =
          new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
              .setConsensusInstance(consensusInstance).setRound(round)
              .setMessage(roundChangeMessage.toJson()).build();

      this.link.broadcast(consensusMessage);
    }
  }

  public void uponRoundChange(ConsensusMessage message) {
    int consensusInstance = message.getConsensusInstance();
    int round = message.getRound();
    int senderId = message.getSenderId();

    logger.info(MessageFormat.format(
        "[{0}]: Received ROUND_CHANGE message from {1} consensus instance {2} round {3}",
        config.getId(), message.getSenderId(), consensusInstance, round));

    roundChangeMessages.addMessage(message);

    if (consensusInstance <= lastDecidedConsensusInstance.get()) {
      logger.info(MessageFormat.format(
          "[{0}]: Received ROUND_CHANGE message for old consensus instance {1}, sending commit",
          config.getId(), consensusInstance));

      CommitQuorumMessage commitQuorumMessage =
          new CommitQuorumMessage(instanceInfo.get(consensusInstance).getCommitQuorum().get());

      ConsensusMessage consensusMessage =
          new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT_QUORUM)
              .setConsensusInstance(consensusInstance).setRound(round)
              .setMessage(commitQuorumMessage.toJson()).build();

      this.link.send(senderId, consensusMessage);

      return;
    }

    Pair<Boolean, Optional<Pair<Integer, String>>> a =
        roundChangeMessages.hasValidRoundChangeQuorum(consensusInstance, round);

    Boolean hasQuorum = a.getFirst();
    Optional<Pair<Integer, String>> highestPrepared = a.getSecond();

    if ((this.config.isLeader(consensusInstance, round)
        || this.config.getByzantineBehavior() == ByzantineBehavior.FakeLeader) && hasQuorum) {
      InstanceInfo instance = instanceInfo.get(consensusInstance);
      int highestPreparedRound = instance.getCurrentRound();
      String highestPreparedValue = instance.getInputValue();

      if (highestPrepared.isPresent()) {
        highestPreparedRound = highestPrepared.get().getFirst();
        highestPreparedValue = highestPrepared.get().getSecond();
      }

      int localConsensusInstance = this.currentConsensusInstance.get();

      receivedRoundChangeQuorum.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
      if (receivedRoundChangeQuorum.get(consensusInstance).put(round, true) != null) {
        logger.info(MessageFormat.format(
            "[{0}]: Already triggered round change quorum rule for consensus instance {1} round {2}, ignoring",
            config.getId(), consensusInstance, round));
        return;
      }

      logger
          .info(MessageFormat.format("[{0}]: Received valid ROUND_CHANGE quorum", config.getId()));

      logger.info(MessageFormat.format("[{0}]: Node is leader, sending PRE-PREPARE message",
          config.getId()));

      this.link.broadcast(this.createConsensusMessage(highestPreparedValue, localConsensusInstance,
          highestPreparedRound));
      return;
    }

    Optional<Integer> minRound =
        roundChangeMessages.hasValidRoundChangeSet(consensusInstance, round);

    if (minRound.isPresent()) {
      receivedRoundChangeSet.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
      if (receivedRoundChangeSet.get(consensusInstance).put(round, true) != null) {
        logger.info(MessageFormat.format(
            "[{0}]: Already triggered round change set rule for consensus instance {1} round {2}, ignoring",
            config.getId(), consensusInstance, round));
        return;
      }

      logger.info(MessageFormat.format("[{0}]: Received valid ROUND_CHANGE set", config.getId()));

      // Set the current round to the minimum round
      synchronized (instanceInfo.get(consensusInstance)) {
        InstanceInfo instance = instanceInfo.get(consensusInstance);
        instance.setCurrentRound(minRound.get());

        restartTimer();

        logger
            .info(MessageFormat.format("[{0}]: Broadcasting ROUND_CHANGE message", config.getId()));

        RoundChangeMessage roundChangeMessage =
            new RoundChangeMessage(instance.getPreparedRound(), instance.getPreparedValue());
        ConsensusMessage consensusMessage =
            new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                .setConsensusInstance(consensusInstance).setRound(round)
                .setMessage(roundChangeMessage.toJson()).build();
        this.link.broadcast(consensusMessage);
      }
    }
  }

  // TODO: verify validity of message as this can be forged by a byzantine process
  public void uponCommitQuorum(ConsensusMessage message) {
    CommitQuorumMessage commitQuorumMessage = message.deserializeCommitQuorumMessage();
    int consensusInstance = message.getConsensusInstance();
    int round = message.getRound();
    String value = commitQuorumMessage.getQuorum().iterator().next().getValue();

    if (consensusInstance <= lastDecidedConsensusInstance.get()) {
      logger.info(MessageFormat.format(
          "[{0}]: Received COMMIT_QUORUM message for already decided consensus instance {1}, ignoring",
          config.getId(), consensusInstance));
      return;
    }

    logger.info(MessageFormat.format("[{0}]: Received COMMIT_QUORUM for (λ, r) = ({1}, {2})",
        config.getId(), consensusInstance, round));

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

                case ACK -> logger.info(MessageFormat.format("[{0}]: Received ACK message from {1}",
                    config.getId(), message.getSenderId()));

                case IGNORE ->
                  logger.info(MessageFormat.format("[{0}]: Received IGNORE message from {1}",
                      config.getId(), message.getSenderId()));

                default ->
                  logger.info(MessageFormat.format("[{0}]: Received unknown message from {1}",
                      config.getId(), message.getSenderId()));

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
