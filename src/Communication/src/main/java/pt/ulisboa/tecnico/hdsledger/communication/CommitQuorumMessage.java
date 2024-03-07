package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.ulisboa.tecnico.hdsledger.utilities.OptionalTypeAdapter;

public class CommitQuorumMessage {

  private Set<CommitMessage> quorum;

  public CommitQuorumMessage(Set<CommitMessage> quorum) {
    this.quorum = quorum;
  }

  public Set<CommitMessage> getQuorum() {
    return quorum;
  }

  public String toJson() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapterFactory(OptionalTypeAdapter.FACTORY);
    Gson gson = gsonBuilder.create();

    return gson.toJson(this);
  }
}