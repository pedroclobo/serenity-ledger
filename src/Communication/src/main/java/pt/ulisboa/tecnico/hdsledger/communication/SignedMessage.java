package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

public class SignedMessage {
  private String messageJson;
  private String signature;

  public SignedMessage(Message message) {
    this.messageJson = message.toJsonString();
    this.signature = null;
  }

  public String getMessageJson() {
    return messageJson;
  }

  public String getSignature() {
    return signature;
  }

  public void setMessageJson(String messageJson) {
    this.messageJson = messageJson;
  }

  public void setSignature(String signature) {
    this.signature = signature;
  }

  public boolean hasSignature() {
    return signature != null;
  }

  public boolean equals(SignedMessage other) {
    return messageJson.equals(other.messageJson) && signature.equals(other.signature);
  }

  public void sign(String privateKeyPath) throws InvalidKeyException, NoSuchAlgorithmException,
      SignatureException, InvalidKeySpecException {
    this.signature = RSACryptography.sign(privateKeyPath, messageJson);
  }

  public boolean verify(String publicKeyPath) throws InvalidKeyException, NoSuchAlgorithmException,
      SignatureException, InvalidKeySpecException {
    return RSACryptography.verify(publicKeyPath, messageJson, this.signature);
  }

  @Override
  public String toString() {
    return "SignedMessage{" + "messageJson='" + messageJson + '\'' + ", signature='" + signature
        + '\'' + '}';
  }
}
