package pt.ulisboa.tecnico.hdsledger.communication.consensus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.utilities.OptionalTypeAdapter;

public class ConsensusMessage extends Message {

  // Consensus instance
  private int consensusInstance;
  // Round
  private int round;
  // Who sent the previous message
  private int replyTo;
  // Id of the previous message
  private int replyToMessageId;
  // Message
  private String message;

  public ConsensusMessage(int senderId, Type type) {
    super(senderId, type);
  }

  public PrePrepareMessage deserializePrePrepareMessage() {
    return new Gson().fromJson(this.message, PrePrepareMessage.class);
  }

  public PrepareMessage deserializePrepareMessage() {
    return new Gson().fromJson(this.message, PrepareMessage.class);
  }

  public CommitMessage deserializeCommitMessage() {
    return new Gson().fromJson(this.message, CommitMessage.class);
  }

  public RoundChangeMessage deserializeRoundChangeMessage() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapterFactory(OptionalTypeAdapter.FACTORY);
    Gson gson = gsonBuilder.create();
    return gson.fromJson(this.message, RoundChangeMessage.class);
  }

  public CommitQuorumMessage deserializeCommitQuorumMessage() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapterFactory(OptionalTypeAdapter.FACTORY);
    Gson gson = gsonBuilder.create();
    return gson.fromJson(this.message, CommitQuorumMessage.class);
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public int getConsensusInstance() {
    return consensusInstance;
  }

  public void setConsensusInstance(int consensusInstance) {
    this.consensusInstance = consensusInstance;
  }

  public int getRound() {
    return round;
  }

  public void setRound(int round) {
    this.round = round;
  }

  public int getReplyTo() {
    return replyTo;
  }

  public void setReplyTo(int replyTo) {
    this.replyTo = replyTo;
  }

  public int getReplyToMessageId() {
    return replyToMessageId;
  }

  public void setReplyToMessageId(int replyToMessageId) {
    this.replyToMessageId = replyToMessageId;
  }
}
