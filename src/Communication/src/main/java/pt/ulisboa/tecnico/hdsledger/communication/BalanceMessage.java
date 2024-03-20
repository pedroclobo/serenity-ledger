package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.PublicKey;

import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

public class BalanceMessage {

  // Serialized source public key
  private String sourcePublicKey;

  public BalanceMessage(PublicKey sourcePublicKey) {
    this.sourcePublicKey = RSACryptography.serializePublicKey(sourcePublicKey);
  }

  public PublicKey getPublicKey() {
    return RSACryptography.deserializePublicKey(sourcePublicKey);
  }

}
