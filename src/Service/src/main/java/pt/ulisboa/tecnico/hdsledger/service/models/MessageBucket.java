package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;

public class MessageBucket {

  private final int f_1;
  private final int quorumSize;

  // Instance -> Round -> Sender ID -> Consensus message
  private final Map<Integer, Map<Integer, Map<Integer, ConsensusMessage>>> prepareBucket =
      new ConcurrentHashMap<>();
  private final Map<Integer, Map<Integer, Map<Integer, ConsensusMessage>>> commitBucket =
      new ConcurrentHashMap<>();
  private final Map<Integer, Map<Integer, Map<Integer, ConsensusMessage>>> roundChangeBucket =
      new ConcurrentHashMap<>();

  public MessageBucket(int nodeCount) {
    int f = Math.floorDiv(nodeCount - 1, 3);
    f_1 = f + 1;
    quorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
  }

  /*
   * Add a message to the bucket
   *
   * @param consensusInstance
   *
   * @param message
   */
  public void addMessage(ConsensusMessage message) {
    int consensusInstance = message.getConsensusInstance();
    int round = message.getRound();

    Map<Integer, Map<Integer, Map<Integer, ConsensusMessage>>> bucket;
    if (message.getType() == ConsensusMessage.Type.PREPARE) {
      bucket = prepareBucket;
    } else if (message.getType() == ConsensusMessage.Type.COMMIT) {
      bucket = commitBucket;
    } else if (message.getType() == ConsensusMessage.Type.ROUND_CHANGE) {
      bucket = roundChangeBucket;
    } else {
      throw new IllegalArgumentException("Invalid message type");
    }

    bucket.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
    bucket.get(consensusInstance).putIfAbsent(round, new ConcurrentHashMap<>());
    bucket.get(consensusInstance).get(round).put(message.getSenderId(), message);
  }

  public boolean justifyPrePrepare(int consensusInstance, int round, String value) {
    return round == 1 || hasUnpreparedRoundChangeQuorum(consensusInstance, round)
        || hasPreparedQuorumWithHighestPreparedEqualToRoundChangeQuorum(consensusInstance, round);
  }

  private boolean hasUnpreparedRoundChangeQuorum(int instance, int round) {
    List<RoundChangeMessage> messages = roundChangeBucket.get(instance).get(round).values().stream()
        .map((message) -> message.deserializeRoundChangeMessage()).collect(Collectors.toList());

    return messages.stream()
        .map((message) -> new Pair<Optional<Integer>, Optional<String>>(message.getPreparedRound(),
            message.getPreparedValue()))
        .filter((pair) -> pair.getFirst().isEmpty() && pair.getSecond().isEmpty())
        .count() >= quorumSize;
  }

  private boolean hasPreparedQuorumWithHighestPreparedEqualToRoundChangeQuorum(int instance,
      int round) {
    Optional<RoundValueClientSignature> highestPrepared = highestPrepared(instance, round);

    if (highestPrepared.isEmpty()) {
      return false;
    }

    int preparedRound = highestPrepared.get().getRound();
    String value = highestPrepared.get().getValue();

    return hasPrepareQuorum(instance, preparedRound, value);
  }

  // Return the highest prepared of the round change messages
  private Optional<RoundValueClientSignature> highestPrepared(int instance, int round) {
    List<RoundChangeMessage> messages = roundChangeBucket.get(instance).get(round).values().stream()
        .map((message) -> message.deserializeRoundChangeMessage()).collect(Collectors.toList());

    if (messages.size() < quorumSize) {
      return Optional.empty();
    }

    // Get the highest prepared round
    Optional<Integer> highestPrepared =
        messages.stream().map((message) -> message.getPreparedRound()).filter(Optional::isPresent)
            .map(Optional::get).max(Integer::compareTo);

    // Get the value of the highest prepared round
    Optional<String> value = messages.stream()
        .filter((message) -> message.getPreparedRound().isPresent()
            && message.getPreparedRound().get() == highestPrepared.get())
        .map((message) -> message.getPreparedValue().get()).findFirst();

    // Get the value signature
    Optional<String> valueSignature = messages.stream()
        .filter((message) -> message.getPreparedRound().isPresent()
            && message.getPreparedRound().get() == highestPrepared.get())
        .map((message) -> message.getPreparedValueSignature().get()).findFirst();

    // Get the client ID
    Optional<Integer> clientId = messages.stream()
        .filter((message) -> message.getPreparedRound().isPresent()
            && message.getPreparedRound().get() == highestPrepared.get())
        .map((message) -> message.getPreparedClientId().get()).findFirst();

    if (highestPrepared.isEmpty() || value.isEmpty() || valueSignature.isEmpty()
        || clientId.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new RoundValueClientSignature(highestPrepared.get(), value.get(),
        clientId.get(), valueSignature.get()));
  }

  public boolean hasPrepareQuorum(int instance, int round, String value) {
    try {
      List<PrepareMessage> messages = prepareBucket.get(instance).get(round).values().stream()
          .map((message) -> message.deserializePrepareMessage()).collect(Collectors.toList());

      return messages.stream().map(message -> message.getValue())
          .filter(messageValue -> messageValue.equals(value)).count() >= quorumSize;
    } catch (NullPointerException e) {
      return false;
    }
  }

  /*
   * Returns the prepared quorum
   */
  public Optional<Set<ConsensusMessage>> getPrepareQuorum(int instance, int round, String value) {
    if (!hasPrepareQuorum(instance, round, value)) {
      return Optional.empty();
    }

    return Optional.of(prepareBucket.get(instance).get(round).values().stream()
        .map((message) -> new Pair<>(message, message.deserializePrepareMessage()))
        .filter((pair) -> pair.getSecond().getValue().equals(value)).map((pair) -> pair.getFirst())
        .collect(Collectors.toSet()));
  }

  private Set<CommitMessage> getCommitQuorum(int instance, int round, String value) {
    return commitBucket.get(instance).get(round).values().stream()
        .map((message) -> message.deserializeCommitMessage())
        .filter((message) -> message.getValue().equals(value)).collect(Collectors.toSet());
  }

  public Pair<Boolean, Optional<Set<CommitMessage>>> hasCommitQuorum(int instance) {
    List<ConsensusMessage> messages = commitBucket.get(instance).values().stream()
        .flatMap(roundMessages -> roundMessages.values().stream()).collect(Collectors.toList());

    if (messages.size() < quorumSize) {
      return new Pair<>(false, Optional.empty());
    }

    List<Pair<String, Integer>> messagePairs = messages.stream().map(
        (message) -> new Pair<>(message.deserializeCommitMessage().getValue(), message.getRound()))
        .collect(Collectors.toList());

    Map<Pair<String, Integer>, Long> frequency = messagePairs.stream()
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

    // Find value with frequency greater than or equal to quorum size
    Optional<Pair<String, Integer>> valueRound = frequency.entrySet().stream().filter((entry) -> {
      return entry.getValue() >= quorumSize;
    }).map((entry) -> entry.getKey()).findFirst();

    if (valueRound.isEmpty()) {
      return new Pair<>(false, Optional.empty());
    }

    int round = valueRound.get().getSecond();
    String value = valueRound.get().getFirst();

    return new Pair<>(true, Optional.of(getCommitQuorum(instance, round, value)));
  }

  /*
   * Checks if there is a valid round change quorum for the instance and round This is the predicate
   * JustifyRoundChange
   */
  public boolean hasRoundChangeQuorum(int instance, int round) {
    return roundChangeBucket.get(instance).get(round).values().size() >= quorumSize
        || hasUnpreparedRoundChangeQuorum(instance, round)
        || hasPreparedQuorumWithHighestPreparedEqualToRoundChangeQuorum(instance, round);
  }

  /**
   * Return the highest prepared round, highest prepared value and respective signature that
   * justifies a round change for instance and round
   */
  public Optional<RoundValueClientSignature> getHighestPrepared(int instance, int round) {
    if (!hasRoundChangeQuorum(instance, round)
        || !hasPreparedQuorumWithHighestPreparedEqualToRoundChangeQuorum(instance, round)) {
      return Optional.empty();
    }

    return highestPrepared(instance, round);
  }

  // Return the round change quorum for the instance, returns the round if the quorum exists
  private Optional<Integer> hasRoundChangeSetWithRoundGreaterThan(int instance, int round) {
    List<ConsensusMessage> messages = roundChangeBucket.get(instance).values().stream()
        .flatMap(roundMessages -> roundMessages.values().stream()).filter((message) -> {
          return message.getRound() > round;
        }).collect(Collectors.toList());

    if (messages.size() < f_1) {
      return Optional.empty();
    }

    // Return the minimum round
    return messages.stream().map((message) -> message.getRound()).min(Integer::compareTo);
  }

  public Optional<Integer> hasValidRoundChangeSet(int instance, int round) {
    return hasRoundChangeSetWithRoundGreaterThan(instance, round);
  }

  public Map<Integer, ConsensusMessage> getMessages(int instance, int round) {
    return prepareBucket.get(instance).get(round);
  }
}
