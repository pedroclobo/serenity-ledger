package pt.ulisboa.tecnico.hdsledger.communication.application;

import com.google.gson.Gson;

public class BalanceResponse {

  private int nonce;
  private int amount;

  public BalanceResponse(int nonce, int amount) {
    this.nonce = nonce;
    this.amount = amount;
  }

  public int getNonce() {
    return nonce;
  }

  public int getAmount() {
    return amount;
  }

  public String toJson() {
    return new Gson().toJson(this);
  }

}
