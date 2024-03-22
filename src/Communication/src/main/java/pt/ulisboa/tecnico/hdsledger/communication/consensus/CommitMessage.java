package pt.ulisboa.tecnico.hdsledger.communication.consensus;

import com.google.gson.Gson;

public class CommitMessage {

  // Serialized block
  private String block;

  public CommitMessage(String block) {
    this.block = block;
  }

  public String getBlock() {
    return block;
  }

  public String toJson() {
    return new Gson().toJson(this);
  }
}