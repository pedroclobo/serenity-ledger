package pt.ulisboa.tecnico.hdsledger.communication.application;

import java.security.PublicKey;

import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

public class TransferRequest {

  private final int nonce;

  // Serialized source public key
  private final String sourcePublicKey;

  // Serialized destination public key
  private final String destinationPublicKey;

  private final int amount;

  public TransferRequest(int nonce, PublicKey sourcePublicKey, PublicKey destinationPublicKey,
      int amount) {
    this.nonce = nonce;
    this.sourcePublicKey = RSACryptography.serializePublicKey(sourcePublicKey);
    this.destinationPublicKey = RSACryptography.serializePublicKey(destinationPublicKey);
    this.amount = amount;
  }

  public int getNonce() {
    return nonce;
  }

  public PublicKey getSourcePublicKey() {
    return RSACryptography.deserializePublicKey(sourcePublicKey);
  }

  public PublicKey getDestinationPublicKey() {
    return RSACryptography.deserializePublicKey(destinationPublicKey);
  }

  public int getAmount() {
    return amount;
  }

}
