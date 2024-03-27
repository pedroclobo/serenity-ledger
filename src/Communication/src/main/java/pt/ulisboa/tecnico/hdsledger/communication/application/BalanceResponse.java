package pt.ulisboa.tecnico.hdsledger.communication.application;

import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.ulisboa.tecnico.hdsledger.utilities.OptionalTypeAdapter;

public class BalanceResponse {

  private boolean successful;
  private int nonce;
  private Optional<Integer> amount;

  public BalanceResponse(boolean successful, int nonce, Optional<Integer> amount) {
    this.successful = successful;
    this.nonce = nonce;
    this.amount = amount;
  }

  public boolean isSuccessful() {
    return successful;
  }

  public int getNonce() {
    return nonce;
  }

  public Optional<Integer> getAmount() {
    return amount;
  }

  public String toJson() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapterFactory(OptionalTypeAdapter.FACTORY);
    Gson gson = gsonBuilder.create();

    return gson.toJson(this);
  }

}
