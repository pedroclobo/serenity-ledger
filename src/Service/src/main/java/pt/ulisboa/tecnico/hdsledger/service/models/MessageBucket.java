package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;

public class MessageBucket {

  // f+1
  private final int f_1;
  // Quorum size
  private final int quorumSize;
  // Instance -> Round -> Sender ID -> Consensus message
  private final Map<Integer, Map<Integer, Map<Integer, ConsensusMessage>>> bucket =
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

    bucket.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
    bucket.get(consensusInstance).putIfAbsent(round, new ConcurrentHashMap<>());
    bucket.get(consensusInstance).get(round).put(message.getSenderId(), message);
  }

  public Optional<String> hasValidPrepareQuorum(int nodeId, int instance, int round) {
    // Create mapping of value to frequency
    HashMap<String, Integer> frequency = new HashMap<>();
    bucket.get(instance).get(round).values().forEach((message) -> {
      PrepareMessage prepareMessage = message.deserializePrepareMessage();
      String value = prepareMessage.getValue();
      frequency.put(value, frequency.getOrDefault(value, 0) + 1);
    });

    // Only one value (if any, thus the optional) will have a frequency
    // greater than or equal to the quorum size
    return frequency.entrySet().stream()
        .filter((Map.Entry<String, Integer> entry) -> entry.getValue() >= quorumSize)
        .map((Map.Entry<String, Integer> entry) -> entry.getKey()).findFirst();
  }

  public Pair<Boolean, Optional<Set<CommitMessage>>> hasValidCommitQuorum(int nodeId, int instance,
      int round) {
    // Create mapping of value to frequency
    HashMap<String, Integer> frequency = new HashMap<>();
    HashMap<String, Set<CommitMessage>> commitQuorum = new HashMap<>();
    bucket.get(instance).get(round).values().forEach((message) -> {
      CommitMessage commitMessage = message.deserializeCommitMessage();
      String value = commitMessage.getValue();
      frequency.put(value, frequency.getOrDefault(value, 0) + 1);
      commitQuorum.putIfAbsent(value, ConcurrentHashMap.newKeySet());
      commitQuorum.get(value).add(commitMessage);
    });

    // Only one value (if any, thus the optional) will have a frequency
    // greater than or equal to the quorum size
    Optional<String> value = frequency.entrySet().stream()
        .filter((Map.Entry<String, Integer> entry) -> entry.getValue() >= quorumSize)
        .map((Map.Entry<String, Integer> entry) -> entry.getKey()).findFirst();

    if (value.isEmpty()) {
      return new Pair<>(false, Optional.empty());
    }
    return new Pair<>(true, Optional.of(commitQuorum.get(value.get())));
  }

  public Optional<Integer> hasValidRoundChangeSet(int instance, int round) {
    // Message count for current instance
    int messageCountForInstance = bucket.get(instance).values().stream()
        .flatMap(roundMessages -> roundMessages.values().stream()).mapToInt(message -> 1).sum();

    if (messageCountForInstance < f_1) {
      return Optional.empty();
    }

    // Message count of message for current instance with round superior to current round
    int messageCountWithSuperiorRound = bucket.get(instance).values().stream()
        .flatMap(roundMessages -> roundMessages.values().stream())
        .map((message) -> message.getRound()).filter((messageRound) -> messageRound > round)
        .mapToInt(r -> 1).sum();

    if (messageCountWithSuperiorRound < f_1) {
      return Optional.empty();
    }

    // Minimum round
    return bucket.get(instance).values().stream()
        .flatMap(roundMessages -> roundMessages.values().stream())
        .map((message) -> message.getRound()).min(Integer::compareTo);
  }

  public boolean hasRoundChangeQuorumUnprepared(int instance, int round) {
    List<RoundChangeMessage> messages = bucket.get(instance).get(round).values().stream()
        .map((message) -> message.deserializeRoundChangeMessage()).collect(Collectors.toList());

    return messages.stream()
        .map((message) -> new Pair<Optional<Integer>, Optional<String>>(message.getPreparedRound(),
            message.getPreparedValue()))
        .filter((pair) -> !pair.getFirst().isPresent() && !pair.getSecond().isPresent())
        .count() >= quorumSize;
  }

  public boolean hasPreparedQuorum(int instance, int round, String value) {
    List<Pair<ConsensusMessage, PrepareMessage>> messages = bucket.get(instance).get(round).values()
        .stream().map((message) -> new Pair<>(message, message.deserializePrepareMessage()))
        .collect(Collectors.toList());

    return messages.stream()
        .map(pair -> new Pair<>(pair.getFirst().getRound(), pair.getSecond().getValue()))
        .filter(pair -> pair.getFirst() == round && pair.getSecond().equals(value))
        .count() >= quorumSize;
  }

  // Return the highest prepared of the round change messages
  public Pair<Optional<Integer>, Optional<String>> highestPrepared(int instance, int round) {
    List<RoundChangeMessage> messages = bucket.get(instance).get(round).values().stream()
        .map((message) -> message.deserializeRoundChangeMessage()).collect(Collectors.toList());

    if (messages.size() < quorumSize) {
      return new Pair<>(Optional.empty(), Optional.empty());
    }

    // Get the highest prepared round
    Optional<Integer> highestPrepared =
        messages.stream().map((message) -> message.getPreparedRound()).filter(Optional::isPresent)
            .map(Optional::get).max(Integer::compareTo);

    if (highestPrepared.isEmpty()) {
      return new Pair<>(Optional.empty(), Optional.empty());
    }

    return new Pair<>(highestPrepared,
        messages.stream().filter((message) -> message.getPreparedRound() == highestPrepared)
            .map((message) -> message.getPreparedValue().get()).findFirst());
  }

  public boolean hasUnpreparedRoundChangeQuorum(int instance, int round) {
    if (bucket.get(instance) == null || bucket.get(instance).get(round) == null
        || bucket.get(instance).get(round).size() < quorumSize) {
      return false;
    }

    long messageCountUnprepared = bucket.get(instance).get(round).values().stream()
        .map((message) -> message.deserializeRoundChangeMessage())
        .filter((roundChangeMessage) -> !roundChangeMessage.getPreparedRound().isPresent()
            && !roundChangeMessage.getPreparedValue().isPresent())
        .count();

    return messageCountUnprepared >= quorumSize;
  }

  public Map<Integer, ConsensusMessage> getMessages(int instance, int round) {
    return bucket.get(instance).get(round);
  }
}
