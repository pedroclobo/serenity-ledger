package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.Serializable;

public class SignedMessage implements Serializable {
  private final String message;
  private final String signature;

  public SignedMessage(String message, String signature) {
    this.message = message;
    this.signature = signature;
  }

  public String getMessage() {
    return message;
  }

  public String getSignature() {
    return signature;
  }

  public String toString() {
    return "Message: " + message + "\nSignature: " + signature;
  }

}
