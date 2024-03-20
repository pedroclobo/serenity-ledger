package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class ClientMessage extends Message {

  // Serialized message
  private String message;
  // TODO: Client signature
  private String clientSignature;

  public ClientMessage(int senderId, Type type, String message, String clientSignature) {
    super(senderId, type);
    this.message = message;
    this.clientSignature = clientSignature;
  }

  public TransferMessage deserializeTransferMessage() {
    return new Gson().fromJson(this.message, TransferMessage.class);
  }

  public BalanceMessage deserializeBalanceMessage() {
    return new Gson().fromJson(this.message, BalanceMessage.class);
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getClientSignature() {
    return clientSignature;
  }

  public void setClientSignature(String clientSignature) {
    this.clientSignature = clientSignature;
  }

}
