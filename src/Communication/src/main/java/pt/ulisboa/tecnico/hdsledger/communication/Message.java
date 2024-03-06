package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.Serializable;

import com.google.gson.Gson;

public class Message implements Serializable {

  // Sender identifier
  private int senderId;
  // Message identifier
  private int messageId;
  // Message type
  private Type type;

  public enum Type {
    APPEND, PRE_PREPARE, PREPARE, COMMIT, ACK, IGNORE, ROUND_CHANGE, COMMIT_QUORUM
  }

  public Message(int senderId, Type type) {
    this.senderId = senderId;
    this.type = type;
  }

  public int getSenderId() {
    return senderId;
  }

  public void setSenderId(int senderId) {
    this.senderId = senderId;
  }

  public int getMessageId() {
    return messageId;
  }

  public void setMessageId(int messageId) {
    this.messageId = messageId;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public String toJsonString() {
    return new Gson().toJson(this);
  }

  @Override
  public String toString() {
    return "Message{" + "senderId='" + senderId + '\'' + ", messageId=" + messageId + ", type="
        + type + '}';
  }
}
