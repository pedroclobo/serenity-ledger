package pt.ulisboa.tecnico.hdsledger.utilities;

public class ProcessConfig {

  public enum ByzantineBehavior {
    None, Drop, FakeLeader, FakeValue, GreedyClient, DrainerClient
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
    int a = (consensusInstance - 1) % N;
    int b = (round - 1) % N;

    return ((a + b) % N + 1) == id;
  }
}
