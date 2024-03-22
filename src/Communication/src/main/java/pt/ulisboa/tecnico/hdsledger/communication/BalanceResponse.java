package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class BalanceResponse {

  private int amount;

  public BalanceResponse(int amount) {
    this.amount = amount;
  }

  public int getAmount() {
    return amount;
  }

  public String toJson() {
    return new Gson().toJson(this);
  }

}
