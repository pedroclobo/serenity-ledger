package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.PublicKey;

import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

public class BalanceRequest {

  // Serialized source public key
  private String sourcePublicKey;

  public BalanceRequest(PublicKey sourcePublicKey) {
    this.sourcePublicKey = RSACryptography.serializePublicKey(sourcePublicKey);
  }

  public PublicKey getPublicKey() {
    return RSACryptography.deserializePublicKey(sourcePublicKey);
  }

}
