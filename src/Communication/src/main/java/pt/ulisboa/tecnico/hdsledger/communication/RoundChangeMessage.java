package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.ulisboa.tecnico.hdsledger.utilities.OptionalTypeAdapter;

public class RoundChangeMessage {

  private Optional<Integer> preparedRound;
  private Optional<String> preparedValue;

  public RoundChangeMessage() {
    this.preparedRound = Optional.empty();
    this.preparedValue = Optional.empty();
  }

  public RoundChangeMessage(Optional<Integer> preparedRound, Optional<String> preparedValue) {
    this.preparedRound = preparedRound;
    this.preparedValue = preparedValue;
  }

  public RoundChangeMessage(int preparedRound, String preparedValue) {
    this.preparedRound = Optional.of(preparedRound);
    this.preparedValue = Optional.of(preparedValue);
  }

  public Optional<Integer> getPreparedRound() {
    return preparedRound;
  }

  public Optional<String> getPreparedValue() {
    return preparedValue;
  }

  public String toJson() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapterFactory(OptionalTypeAdapter.FACTORY);
    Gson gson = gsonBuilder.create();

    return gson.toJson(this);
  }
}
