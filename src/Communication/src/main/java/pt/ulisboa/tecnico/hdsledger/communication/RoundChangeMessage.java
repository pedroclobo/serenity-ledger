package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.ulisboa.tecnico.hdsledger.utilities.OptionalTypeAdapter;

public class RoundChangeMessage {

  private Optional<Integer> preparedRound;
  private Optional<Integer> preparedClientId;
  private Optional<String> preparedValue;
  private Optional<String> preparedValueSignature;
  private Optional<Set<ConsensusMessage>> preparedQuorum;

  public RoundChangeMessage() {
    this.preparedRound = Optional.empty();
    this.preparedClientId = Optional.empty();
    this.preparedValue = Optional.empty();
    this.preparedValueSignature = Optional.empty();
    this.preparedQuorum = Optional.empty();
  }

  public RoundChangeMessage(Optional<Integer> preparedRound, Optional<Integer> preparedClientId,
      Optional<String> preparedValue, Optional<String> preparedValueSignature,
      Optional<Set<ConsensusMessage>> preparedQuorum) {
    this.preparedRound = preparedRound;
    this.preparedClientId = preparedClientId;
    this.preparedValue = preparedValue;
    this.preparedValueSignature = preparedValueSignature;
    this.preparedQuorum = preparedQuorum;
  }

  public RoundChangeMessage(int preparedRound, int preparedClientId, String preparedValue,
      String preparedValueSignature, Set<ConsensusMessage> preparedQuorum) {
    this.preparedRound = Optional.of(preparedRound);
    this.preparedClientId = Optional.of(preparedClientId);
    this.preparedValue = Optional.of(preparedValue);
    this.preparedValueSignature = Optional.of(preparedValueSignature);
    this.preparedQuorum = Optional.of(preparedQuorum);
  }

  public Optional<Integer> getPreparedRound() {
    return preparedRound;
  }

  public Optional<String> getPreparedValue() {
    return preparedValue;
  }

  public Optional<Integer> getPreparedClientId() {
    return preparedClientId;
  }

  public Optional<String> getPreparedValueSignature() {
    return preparedValueSignature;
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
