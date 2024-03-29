package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import pt.ulisboa.tecnico.hdsledger.communication.consensus.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.RoundChangeMessage;

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

  public boolean justifyPrePrepare(int consensusInstance, int round) {
    return round == 1 || hasUnpreparedRoundChangeQuorum(consensusInstance, round)
        || hasPreparedQuorumWithHighestPreparedEqualToRoundChangeQuorum(consensusInstance, round);
  }

  private boolean hasUnpreparedRoundChangeQuorum(int instance, int round) {
    List<RoundChangeMessage> messages = roundChangeBucket.get(instance).get(round).values().stream()
        .map(ConsensusMessage::deserializeRoundChangeMessage).toList();

    return messages.stream()
        .map((message) -> new Pair<>(message.getPreparedRound(), message.getPreparedBlock()))
        .filter((pair) -> pair.first().isEmpty() && pair.second().isEmpty()).count() >= quorumSize;
  }

  private boolean hasPreparedQuorumWithHighestPreparedEqualToRoundChangeQuorum(int instance,
      int round) {
    Optional<Pair<Integer, String>> highestPrepared = highestPrepared(instance, round);

    if (highestPrepared.isEmpty()) {
      return false;
    }

    int preparedRound = highestPrepared.get().first();
    String value = highestPrepared.get().second();

    return hasPrepareQuorum(instance, preparedRound, value);
  }

  // Return the highest prepared of the round change messages
  private Optional<Pair<Integer, String>> highestPrepared(int instance, int round) {
    List<RoundChangeMessage> messages = roundChangeBucket.get(instance).get(round).values().stream()
        .map(ConsensusMessage::deserializeRoundChangeMessage).toList();

    if (messages.size() < quorumSize) {
      return Optional.empty();
    }

    // Get the highest prepared round
    Optional<Integer> highestPrepared = messages.stream().map(RoundChangeMessage::getPreparedRound)
        .filter(Optional::isPresent).map(Optional::get).max(Integer::compareTo);

    // Get the value of the highest prepared round
    Optional<String> value = messages.stream()
        .filter((message) -> message.getPreparedRound().isPresent()
            && message.getPreparedRound().get().equals(highestPrepared.get()))
        .map((message) -> message.getPreparedBlock().get()).findFirst();

    if (highestPrepared.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new Pair<>(highestPrepared.get(), value.get()));
  }

  public boolean hasPrepareQuorum(int instance, int round, String block) {
    try {
      List<PrepareMessage> messages = prepareBucket.get(instance).get(round).values().stream()
          .map(ConsensusMessage::deserializePrepareMessage).toList();

      return messages.stream().map(PrepareMessage::block)
          .filter(messageBlock -> messageBlock.equals(block)).count() >= quorumSize;
    } catch (NullPointerException e) {
      return false;
    }
  }

  /*
   * Returns the prepared quorum
   */
  public Optional<Set<ConsensusMessage>> getPrepareQuorum(int instance, int round, String block) {
    if (!hasPrepareQuorum(instance, round, block)) {
      return Optional.empty();
    }

    return Optional.of(prepareBucket.get(instance).get(round).values().stream()
        .map((message) -> new Pair<>(message, message.deserializePrepareMessage()))
        .filter((pair) -> pair.second().block().equals(block)).map(Pair::first)
        .collect(Collectors.toSet()));
  }

  public boolean hasCommitQuorum(int instance) {
    // Get all commit messages for instance
    List<ConsensusMessage> messages = commitBucket.get(instance).values().stream()
        .flatMap(commitMessages -> commitMessages.values().stream()).toList();

    if (messages.size() < quorumSize) {
      return false;
    }

    // Group messages by (round, value)
    Map<Pair<Integer, String>, Long> frequency = messages.stream()
        .map(
            (message) -> new Pair<>(message.getRound(), message.deserializeCommitMessage().block()))
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

    // Find if there is a (round, value) with frequency greater than or equal to quorum size
    return frequency.entrySet().stream().anyMatch((entry) -> entry.getValue() >= quorumSize);
  }

  public Optional<Set<ConsensusMessage>> getCommitQuorum(int instance) {
    // Get all commit messages for instance
    List<ConsensusMessage> messages = commitBucket.get(instance).values().stream()
        .flatMap(roundMessages -> roundMessages.values().stream()).toList();

    if (messages.size() < quorumSize) {
      return Optional.empty();
    }

    // Group messages by (round, value)
    Map<Pair<Integer, String>, Long> frequency = messages.stream()
        .map(
            (message) -> new Pair<>(message.getRound(), message.deserializeCommitMessage().block()))
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

    // Retrieve the (round, value) with frequency greater than or equal to quorum size
    Optional<Pair<Integer, String>> roundValue = frequency.entrySet().stream()
        .filter((entry) -> entry.getValue() >= quorumSize).map(Map.Entry::getKey).findFirst();

    return roundValue.map((pair) -> commitBucket.get(instance).get(pair.first()).values().stream()
        .map(m -> new Pair<>(m, m.deserializeCommitMessage()))
        .filter(p -> p.second().block().equals(pair.second())).map(Pair::first)
        .collect(Collectors.toSet()));
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
  public Optional<Pair<Integer, String>> getHighestPrepared(int instance, int round) {
    if (!hasRoundChangeQuorum(instance, round)
        || !hasPreparedQuorumWithHighestPreparedEqualToRoundChangeQuorum(instance, round)) {
      return Optional.empty();
    }

    return highestPrepared(instance, round);
  }

  // Return the round change quorum for the instance, returns the round if the quorum exists
  public Optional<Integer> getMinRoundOfRoundChangeSet(int instance, int round) {
    List<ConsensusMessage> messages = roundChangeBucket.get(instance).values().stream()
        .flatMap(m -> m.values().stream()).filter((m) -> m.getRound() > round).toList();

    if (messages.size() < f_1) {
      return Optional.empty();
    }

    // Return the minimum round
    return messages.stream().map(ConsensusMessage::getRound).min(Integer::compareTo);
  }

  public boolean hasValidRoundChangeSet(int instance, int round) {
    return getMinRoundOfRoundChangeSet(instance, round).isPresent();
  }

}
