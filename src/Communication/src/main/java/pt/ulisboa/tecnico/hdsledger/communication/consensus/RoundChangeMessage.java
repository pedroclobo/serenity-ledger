package pt.ulisboa.tecnico.hdsledger.communication.consensus;

import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.ulisboa.tecnico.hdsledger.utilities.OptionalTypeAdapter;

public class RoundChangeMessage {

  private Optional<Integer> preparedRound;
  private Optional<String> preparedBlock;
  private Optional<Set<ConsensusMessage>> preparedQuorum;

  public RoundChangeMessage() {
    this.preparedRound = Optional.empty();
    this.preparedBlock = Optional.empty();
    this.preparedQuorum = Optional.empty();
  }

  public RoundChangeMessage(Optional<Integer> preparedRound, Optional<String> preparedBlock,
      Optional<Set<ConsensusMessage>> preparedQuorum) {
    this.preparedRound = preparedRound;
    this.preparedBlock = preparedBlock;
    this.preparedQuorum = preparedQuorum;
  }

  public RoundChangeMessage(int preparedRound, String preparedBlock,
      Set<ConsensusMessage> preparedQuorum) {
    this.preparedRound = Optional.of(preparedRound);
    this.preparedBlock = Optional.of(preparedBlock);
    this.preparedQuorum = Optional.of(preparedQuorum);
  }

  public Optional<Integer> getPreparedRound() {
    return preparedRound;
  }

  public Optional<String> getPreparedBlock() {
    return preparedBlock;
  }

  public Optional<Set<ConsensusMessage>> getPreparedQuorum() {
    return preparedQuorum;
  }

  public String toJson() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapterFactory(OptionalTypeAdapter.FACTORY);
    Gson gson = gsonBuilder.create();

    return gson.toJson(this);
  }
}
