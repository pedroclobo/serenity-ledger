package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

public class AppendMessage extends Message {

  private String value;
  private String valueSignature;

  public AppendMessage(int senderId, Type type, String value) {
    super(senderId, type);
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getValueSignature() {
    return valueSignature;
  }

  public void signValue(String privateKeyPath) throws InvalidKeyException, NoSuchAlgorithmException,
      SignatureException, InvalidKeySpecException {
    this.valueSignature = RSACryptography.sign(privateKeyPath, value);
  }

  public boolean verifyValueSignature(String publicKeyPath) throws InvalidKeyException,
      NoSuchAlgorithmException, SignatureException, InvalidKeySpecException {
    return RSACryptography.verify(publicKeyPath, this.value, this.valueSignature);
  }

  public AppendMessage deserialize() {
    return new Gson().fromJson(value, AppendMessage.class);
  }

  @Override
  public String toString() {
    return "AppendMessage [value=" + value + ", valueSignature=" + valueSignature + "]";
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!(obj instanceof AppendMessage))
      return false;

    AppendMessage other = (AppendMessage) obj;

    return value.equals(other.value);
  }

}
