package pt.ulisboa.tecnico.hdsledger.communication.application;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;

public class ClientRequest extends Message {

  // Serialized message
  private String message;
  private String signature;

  public ClientRequest(int senderId, Type type, String message, String signature) {
    super(senderId, type);
    this.message = message;
    this.signature = signature;
  }

  public TransferRequest deserializeTransferMessage() {
    return new Gson().fromJson(this.message, TransferRequest.class);
  }

  public BalanceRequest deserializeBalanceMessage() {
    return new Gson().fromJson(this.message, BalanceRequest.class);
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getSignature() {
    return signature;
  }

  public void setSignature(String signature) {
    this.signature = signature;
  }

}
