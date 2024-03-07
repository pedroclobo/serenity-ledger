package pt.ulisboa.tecnico.hdsledger.service.models;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Optional;
import java.util.Set;

import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

public class InstanceInfo {

  private int currentRound;
  private Optional<Integer> preparedRound;
  private Optional<String> preparedValue;
  private String inputValue;
  private Optional<CommitMessage> commitMessage;
  private Optional<Integer> committedRound;
  private Optional<Set<CommitMessage>> commitQuorum;
  private String valueSignature;
  private int clientId;

  public InstanceInfo(String inputValue, int clientId, String valueSignature) {
    this.currentRound = 1;
    this.preparedRound = Optional.empty();
    this.preparedValue = Optional.empty();
    this.inputValue = inputValue;
    this.commitMessage = Optional.empty();
    this.committedRound = Optional.empty();
    this.commitQuorum = Optional.empty();
    this.valueSignature = valueSignature;
    this.clientId = clientId;
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

  public Optional<String> getPreparedValue() {
    return preparedValue;
  }

  public void setPreparedValue(String preparedValue) {
    this.preparedValue = Optional.of(preparedValue);
  }

  public String getInputValue() {
    return inputValue;
  }

  public void setInputValue(String inputValue) {
    this.inputValue = inputValue;
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

  public String getValueSignature() {
    return valueSignature;
  }

  public int getClientId() {
    return clientId;
  }

  public boolean verifyValueSignature(String publicKeyPath, String value)
      throws InvalidKeySpecException, NoSuchAlgorithmException {
    return RSACryptography.verify(publicKeyPath, value, this.valueSignature);
  }
}
