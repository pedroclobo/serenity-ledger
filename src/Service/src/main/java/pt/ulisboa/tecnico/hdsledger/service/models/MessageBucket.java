package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;

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

  // Return the minimum round change value in the set
  // Makes sure the number of round change messages is greater than f+1
  public Optional<Integer> hasValidRoundChangeSet(int instance, int round) {
    if (bucket.get(instance).get(round).size() < f_1) {
      return Optional.empty();
    }
    return bucket.get(instance).get(round).values().stream().map((message) -> message.getRound())
        .min(Integer::compareTo);
  }

  public Pair<Boolean, Optional<Pair<Integer, String>>> hasValidRoundChangeQuorum(int instance,
      int round) {
    if (bucket.get(instance) == null || bucket.get(instance).get(round) == null
        || bucket.get(instance).get(round).size() < quorumSize) {
      return new Pair<>(false, Optional.empty());
    }

    Optional<Integer> highestPrepared = bucket.get(instance).get(round).values().stream()
        .map((message) -> message.deserializeRoundChangeMessage().getPreparedRound())
        .filter(Optional::isPresent).map(Optional::get).max(Integer::compareTo);

    if (highestPrepared.isEmpty()) {
      return new Pair<>(true, Optional.empty());
    }

    String highestPreparedValue = bucket.get(instance).get(round).values().stream()
        .map((message) -> message.deserializeRoundChangeMessage())
        .filter((roundChangeMessage) -> roundChangeMessage.getPreparedRound() == highestPrepared)
        .map((roundChangeMessage) -> roundChangeMessage.getPreparedValue().get()).findFirst().get();

    return new Pair<>(true, Optional.of(new Pair<>(highestPrepared.get(), highestPreparedValue)));
  }

  public Map<Integer, ConsensusMessage> getMessages(int instance, int round) {
    return bucket.get(instance).get(round);
  }
}
