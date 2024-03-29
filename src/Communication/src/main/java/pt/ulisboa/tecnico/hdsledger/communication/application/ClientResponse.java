package pt.ulisboa.tecnico.hdsledger.communication.application;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.utilities.OptionalTypeAdapter;

public class ClientResponse extends Message {

  // Serialized message
  private final String message;

  public ClientResponse(int senderId, Type type, String message) {
    super(senderId, type);
    this.message = message;
  }

  public TransferResponse deserializeTransferResponse() {
    return new Gson().fromJson(this.message, TransferResponse.class);
  }

  public BalanceResponse deserializeBalanceResponse() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapterFactory(OptionalTypeAdapter.FACTORY);
    Gson gson = gsonBuilder.create();

    return gson.fromJson(this.message, BalanceResponse.class);
  }

}
