package pt.ulisboa.tecnico.hdsledger.communication.application;

import com.google.gson.Gson;

public record TransferResponse(boolean successful, int nonce, int amount) {

  public String toJson() {
    return new Gson().toJson(this);
  }

}
