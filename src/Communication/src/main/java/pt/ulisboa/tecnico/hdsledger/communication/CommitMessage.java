package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

public class CommitMessage {

  private String value;
  private int clientId;
  private String valueSignature;

  public CommitMessage(String value, int clientId, String valueSignature) {
    this.value = value;
    this.clientId = clientId;
    this.valueSignature = valueSignature;
  }

  public String getValue() {
    return value;
  }

  public int getClientId() {
    return clientId;
  }

  public String getValueSignature() {
    return valueSignature;
  }

  public boolean verifyValueSignature(String publicKeyPath, String value)
      throws InvalidKeySpecException, NoSuchAlgorithmException {
    return RSACryptography.verify(publicKeyPath, value, this.valueSignature);
  }

  public String toJson() {
    return new Gson().toJson(this);
  }
}
