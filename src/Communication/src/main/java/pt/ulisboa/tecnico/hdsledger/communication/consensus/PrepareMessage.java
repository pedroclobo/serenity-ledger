package pt.ulisboa.tecnico.hdsledger.communication.consensus;

import com.google.gson.Gson;

public record PrepareMessage(String block) {

  public String toJson() {
    return new Gson().toJson(this);
  }
}
