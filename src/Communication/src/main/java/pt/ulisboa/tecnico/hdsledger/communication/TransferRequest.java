package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.PublicKey;

import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

public class TransferRequest {

  // Serialized source public key
  private String sourcePublicKey;

  // Serialized destination public key
  private String destinationPublicKey;

  private int amount;

  public TransferRequest(PublicKey sourcePublicKey, PublicKey destinationPublicKey, int amount) {
    this.sourcePublicKey = RSACryptography.serializePublicKey(sourcePublicKey);
    this.destinationPublicKey = RSACryptography.serializePublicKey(destinationPublicKey);
    this.amount = amount;
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
