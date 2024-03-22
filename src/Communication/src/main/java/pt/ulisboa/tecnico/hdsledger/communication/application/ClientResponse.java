package pt.ulisboa.tecnico.hdsledger.communication.application;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;

public class ClientResponse extends Message {

  // Serialized message
  private String message;
  private String signature;

  public ClientResponse(int senderId, Type type, String message, String signature) {
    super(senderId, type);
    this.message = message;
    this.signature = signature;
  }

  public TransferResponse deserializeTransferRequest() {
    return new Gson().fromJson(this.message, TransferResponse.class);
  }

  public BalanceResponse deserializeBalanceRequest() {
    return new Gson().fromJson(this.message, BalanceResponse.class);
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
