package pt.ulisboa.tecnico.hdsledger.service;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import pt.ulisboa.tecnico.hdsledger.library.Library;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

abstract class ByzantineBehaviorTest {

  private String nodesConfigPath = "src/main/resources/";
  private String clientsConfigPath = "../Client/src/main/resources/";

  protected ArrayList<Node> nodes;
  protected Library library;

  public ByzantineBehaviorTest(String nodeConfigFile, String clientConfigFile) {
    nodesConfigPath += nodeConfigFile;
    clientsConfigPath += clientConfigFile;
  }

  public ProcessConfig[] parseConfigs(String configPath) {
    return ProcessConfigBuilder.fromFile(configPath);
  }

  @BeforeEach
  public final void setUp() {
    nodes = new ArrayList<>();

    ProcessConfig[] nodesConfig = parseConfigs(nodesConfigPath);
    ProcessConfig[] clientsConfig = parseConfigs(clientsConfigPath);
    for (ProcessConfig nodeConfig : nodesConfig) {
      Node node = new Node(nodeConfig.getId(), nodesConfig, clientsConfig, true);
      nodes.add(node);
      node.start();
    }

    // Make the library use the client port
    // A new array has to be created or deep copy the original
    ProcessConfig[] newNodesConfig = parseConfigs(nodesConfigPath);
    ProcessConfig[] newClientsConfig = parseConfigs(clientsConfigPath);
    for (ProcessConfig nodeConfig : newNodesConfig) {
      nodeConfig.setPort(nodeConfig.getClientPort());
    }
    library = new Library(newNodesConfig, newClientsConfig[0], false);
  }

  @AfterEach
  public final void tearDown() {
    for (Node node : nodes) {
      node.shutdown();
    }
    library.shutdown();
  }

}
