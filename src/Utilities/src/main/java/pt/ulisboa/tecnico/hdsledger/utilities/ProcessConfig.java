package pt.ulisboa.tecnico.hdsledger.utilities;

public class ProcessConfig {

  public enum ByzantineBehavior {
    None, Drop,
  }

  private int N;

  private int id;
  private String hostname;
  private int port;
  private int clientPort;
  private String publicKeyPath;
  private String privateKeyPath;
  private ByzantineBehavior byzantineBehavior;

  public ProcessConfig() {}

  public int getN() {
    return N;
  }

  public void setN(int N) {
    this.N = N;
  }

  public int getId() {
    return id;
  }

  public String getHostname() {
    return hostname;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public int getClientPort() {
    return clientPort;
  }

  public String getPublicKeyPath() {
    return publicKeyPath;
  }

  public String getPrivateKeyPath() {
    return privateKeyPath;
  }

  public ByzantineBehavior getByzantineBehavior() {
    return byzantineBehavior;
  }

  public boolean isLeader(int consensusInstance, int round) {
    if (round == 1) {
      return id == 1;
    } else if (round == 2) {
      return id == 2;
    } else if (round == 3) {
      return id == 3;
    } else if (round == 4) {
      return id == 4;
    }
    return isLeader(consensusInstance, round - 4);
  }

}
