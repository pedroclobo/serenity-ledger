package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

public class SignedMessage {
  private final String messageJson;
  private String signature;

  public SignedMessage(Message message) {
    this.messageJson = message.toJsonString();
    this.signature = null;
  }

  public String getMessageJson() {
    return messageJson;
  }

  public boolean equals(SignedMessage other) {
    return messageJson.equals(other.messageJson) && signature.equals(other.signature);
  }

  public void sign(String privateKeyPath) throws InvalidKeyException, NoSuchAlgorithmException,
      SignatureException, InvalidKeySpecException {
    PrivateKey privateKey = RSACryptography.readPrivateKey(privateKeyPath);
    this.signature = RSACryptography.sign(messageJson, privateKey);
  }

  public boolean verify(String publicKeyPath) throws InvalidKeyException, NoSuchAlgorithmException,
      SignatureException, InvalidKeySpecException {
    PublicKey publicKey = RSACryptography.readPublicKey(publicKeyPath);
    return RSACryptography.verify(messageJson, publicKey, this.signature);
  }

  @Override
  public String toString() {
    return "SignedMessage{" + "messageJson='" + messageJson + '\'' + ", signature='" + signature
        + '\'' + '}';
  }
}
