package pt.ulisboa.tecnico.hdsledger.communication.application;

import java.security.PublicKey;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

public class TransferResponse {

  private boolean successful;

  private int nonce;

  // Serialized source public key
  private String sourcePublicKey;

  // Serialized destination public key
  private String destinationPublicKey;

  private int amount;

  public TransferResponse(boolean successful, int nonce, PublicKey sourcePublicKey,
      PublicKey destinationPublicKey, int amount) {
    this.successful = successful;
    this.nonce = nonce;
    this.sourcePublicKey = RSACryptography.serializePublicKey(sourcePublicKey);
    this.destinationPublicKey = RSACryptography.serializePublicKey(destinationPublicKey);
    this.amount = amount;
  }

  public boolean isSuccessful() {
    return successful;
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

  public String toJson() {
    return new Gson().toJson(this);
  }

}
