package pt.ulisboa.tecnico.hdsledger.communication.application;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

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

  public boolean verifySignature(String publicKeyPath) throws InvalidKeyException,
      NoSuchAlgorithmException, SignatureException, InvalidKeySpecException {
    PublicKey publicKey = RSACryptography.readPublicKey(publicKeyPath);
    return RSACryptography.verify(this.message, publicKey, this.signature);
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof ClientRequest)) {
      return false;
    }
    final ClientRequest other = (ClientRequest) obj;

    return getSenderId() == other.getSenderId() && getType() == other.getType()
        && message.equals(other.message) && signature.equals(other.signature);
  }
}
