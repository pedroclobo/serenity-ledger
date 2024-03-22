package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.hdsledger.communication.consensus.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.ConsensusMessage;

public class InstanceInfo {

  private int currentRound;

  private Optional<Integer> preparedRound;
  private Optional<Block> preparedBlock;
  private Optional<Set<ConsensusMessage>> preparedQuorum;

  private Block inputBlock;

  private Optional<CommitMessage> commitMessage;
  private Optional<Integer> committedRound;
  private Optional<Set<CommitMessage>> commitQuorum;

  private Optional<Block> decidedBlock;

  private Map<Integer, Boolean> triggeredPrePrepareRule;
  private Map<Integer, Boolean> triggeredPrepareQuorumRule;
  private Map<Integer, Boolean> triggeredCommitQuorumRule;
  private Map<Integer, Boolean> triggeredRoundChangeSetRule;
  private Map<Integer, Boolean> triggeredRoundChangeQuorumRule;

  public InstanceInfo(Block inputBlock) {
    this.currentRound = 1;

    this.preparedRound = Optional.empty();
    this.preparedBlock = Optional.empty();
    this.preparedQuorum = Optional.empty();

    this.inputBlock = inputBlock;

    this.commitMessage = Optional.empty();
    this.committedRound = Optional.empty();
    this.commitQuorum = Optional.empty();

    this.decidedBlock = Optional.empty();

    this.triggeredPrePrepareRule = new ConcurrentHashMap<>();
    this.triggeredPrepareQuorumRule = new ConcurrentHashMap<>();
    this.triggeredCommitQuorumRule = new ConcurrentHashMap<>();
    this.triggeredRoundChangeSetRule = new ConcurrentHashMap<>();
    this.triggeredRoundChangeQuorumRule = new ConcurrentHashMap<>();
  }

  public int getCurrentRound() {
    return currentRound;
  }

  public void setCurrentRound(int currentRound) {
    this.currentRound = currentRound;
  }

  public Optional<Integer> getPreparedRound() {
    return preparedRound;
  }

  public void setPreparedRound(int preparedRound) {
    this.preparedRound = Optional.of(preparedRound);
  }

  public Optional<Block> getPreparedBlock() {
    return preparedBlock;
  }

  public void setPreparedBlock(Block preparedBlock) {
    this.preparedBlock = Optional.of(preparedBlock);
  }

  public Optional<Set<ConsensusMessage>> getPreparedQuorum() {
    return preparedQuorum;
  }

  public void setPreparedQuorum(Optional<Set<ConsensusMessage>> preparedQuorum) {
    this.preparedQuorum = preparedQuorum;
  }

  public Block getInputBlock() {
    return inputBlock;
  }

  public void setInputBlock(Block inputBlock) {
    this.inputBlock = inputBlock;
  }

  public Optional<CommitMessage> getCommitMessage() {
    return commitMessage;
  }

  public void setCommitMessage(CommitMessage commitMessage) {
    this.commitMessage = Optional.of(commitMessage);
  }

  public Optional<Integer> getCommittedRound() {
    return committedRound;
  }

  public void setCommittedRound(int committedRound) {
    this.committedRound = Optional.of(committedRound);
  }

  public Optional<Set<CommitMessage>> getCommitQuorum() {
    return commitQuorum;
  }

  public void setCommitQuorum(Set<CommitMessage> commitQuorum) {
    this.commitQuorum = Optional.of(commitQuorum);
  }

  public Optional<Block> getDecidedBlock() {
    return decidedBlock;
  }

  public void setDecidedBlock(Block decidedBlock) {
    this.decidedBlock = Optional.of(decidedBlock);
  }

  public boolean triggeredPrePrepareRule(int round) {
    return triggeredPrePrepareRule.getOrDefault(round, false);
  }

  public void setTriggeredPrePrepareRule(int round) {
    this.triggeredPrePrepareRule.put(round, true);
  }

  public boolean triggeredPrepareQuorumRule(int round) {
    return triggeredPrepareQuorumRule.getOrDefault(round, false);
  }

  public void setTriggeredPrepareQuorumRule(int round) {
    this.triggeredPrepareQuorumRule.put(round, true);
  }

  public boolean triggeredCommitQuorumRule(int round) {
    return triggeredCommitQuorumRule.getOrDefault(round, false);
  }

  public void setTriggeredCommitQuorumRule(int round) {
    this.triggeredCommitQuorumRule.put(round, true);
  }

  public boolean triggeredRoundChangeSetRule(int round) {
    return triggeredRoundChangeSetRule.getOrDefault(round, false);
  }

  public void setTriggeredRoundChangeSetRule(int round) {
    this.triggeredRoundChangeSetRule.put(round, true);
  }

  public boolean triggeredRoundChangeQuorumRule(int round) {
    return triggeredRoundChangeQuorumRule.getOrDefault(round, false);
  }

  public void setTriggeredRoundChangeQuorumRule(int round) {
    this.triggeredRoundChangeQuorumRule.put(round, true);
  }
}
