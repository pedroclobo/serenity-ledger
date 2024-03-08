package pt.ulisboa.tecnico.hdsledger.service.models;

public class RoundValueClientSignature {
  private int round;
  private String value;
  private int clientId;
  private String valueSignature;

  public RoundValueClientSignature(int round, String value, int clientId, String valueSignature) {
    this.round = round;
    this.value = value;
    this.clientId = clientId;
    this.valueSignature = valueSignature;
  }

  public int getRound() {
    return round;
  }

  public String getValue() {
    return value;
  }

  public int getClientId() {
    return clientId;
  }

  public String getValueSignature() {
    return valueSignature;
  }
}
