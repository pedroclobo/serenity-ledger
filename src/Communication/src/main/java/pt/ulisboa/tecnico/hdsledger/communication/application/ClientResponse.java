package pt.ulisboa.tecnico.hdsledger.communication.application;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.Message;

public class ClientResponse extends Message {

  // Serialized message
  private String message;

  public ClientResponse(int senderId, Type type, String message) {
    super(senderId, type);
    this.message = message;
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

}
