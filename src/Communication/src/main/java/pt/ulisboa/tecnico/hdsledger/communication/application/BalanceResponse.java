package pt.ulisboa.tecnico.hdsledger.communication.application;

import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.ulisboa.tecnico.hdsledger.utilities.OptionalTypeAdapter;

public record BalanceResponse(boolean successful, int nonce, Optional<Integer> amount) {

  public String toJson() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapterFactory(OptionalTypeAdapter.FACTORY);
    Gson gson = gsonBuilder.create();

    return gson.toJson(this);
  }

}
