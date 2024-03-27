package pt.ulisboa.tecnico.hdsledger.communication.consensus;

import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.ulisboa.tecnico.hdsledger.utilities.OptionalTypeAdapter;

public class CommitQuorumMessage {

  private Set<ConsensusMessage> quorum;

  public CommitQuorumMessage(Set<ConsensusMessage> quorum) {
    this.quorum = quorum;
  }

  public Set<ConsensusMessage> getQuorum() {
    return quorum;
  }

  public String toJson() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapterFactory(OptionalTypeAdapter.FACTORY);
    Gson gson = gsonBuilder.create();

    return gson.toJson(this);
  }
}
