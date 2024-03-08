package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.ulisboa.tecnico.hdsledger.utilities.OptionalTypeAdapter;

public class RoundChangeMessage {

  private Optional<Integer> preparedRound;
  private Optional<String> preparedValue;
  private Optional<Integer> preparedClientId;
  private Optional<String> preparedValueSignature;

  public RoundChangeMessage() {
    this.preparedRound = Optional.empty();
    this.preparedValue = Optional.empty();
  }

  public RoundChangeMessage(Optional<Integer> preparedRound, Optional<String> preparedValue,
      Optional<Integer> preparedClientId, Optional<String> preparedValueSignature) {
    this.preparedRound = preparedRound;
    this.preparedValue = preparedValue;
    this.preparedClientId = preparedClientId;
    this.preparedValueSignature = preparedValueSignature;
  }

  public RoundChangeMessage(int preparedRound, String preparedValue, int preparedClientId,
      String preparedValueSignature) {
    this.preparedRound = Optional.of(preparedRound);
    this.preparedValue = Optional.of(preparedValue);
    this.preparedClientId = Optional.of(preparedClientId);
    this.preparedValueSignature = Optional.of(preparedValueSignature);
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

  public String toJson() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapterFactory(OptionalTypeAdapter.FACTORY);
    Gson gson = gsonBuilder.create();

    return gson.toJson(this);
  }
}
