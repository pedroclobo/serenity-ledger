package pt.ulisboa.tecnico.hdsledger.communication.application;

import java.security.PublicKey;

import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

public class BalanceRequest {

  private final int nonce;
  // Serialized source public key
  private final String sourcePublicKey;

  public BalanceRequest(int nonce, PublicKey sourcePublicKey) {
    this.nonce = nonce;
    this.sourcePublicKey = RSACryptography.serializePublicKey(sourcePublicKey);
  }

  public int getNonce() {
    return nonce;
  }

  public PublicKey getPublicKey() {
    return RSACryptography.deserializePublicKey(sourcePublicKey);
  }

}
