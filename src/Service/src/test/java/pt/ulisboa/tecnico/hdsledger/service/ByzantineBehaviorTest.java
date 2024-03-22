package pt.ulisboa.tecnico.hdsledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pt.ulisboa.tecnico.hdsledger.communication.application.BalanceResponse;
import pt.ulisboa.tecnico.hdsledger.library.Library;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

public abstract class ByzantineBehaviorTest {

  private static String keysPath = "../PKI/src/main/resources/keys/";
  private static int BLOCK_SIZE = 1;

  private String nodesConfigPath = "src/main/resources/";
  private String clientsConfigPath = "../Client/src/main/resources/";

  protected ArrayList<Node> nodes;
  protected Library library;

  protected int f;
  protected int quorumSize;

  public ByzantineBehaviorTest(String nodeConfigFile, String clientConfigFile) {
    nodesConfigPath += nodeConfigFile;
    clientsConfigPath += clientConfigFile;

    ProcessConfig[] nodesConfig = parseConfigs(nodesConfigPath);
    f = (nodesConfig.length - 1) / 3;
    quorumSize = 2 * f + 1;
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
      Node node = new Node(nodeConfig.getId(), nodesConfig, clientsConfig, BLOCK_SIZE, false);
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

  @Test
  void checkBalance() {
    for (Node node : nodes) {
      assertEquals(0, node.getNodeService().getLedger().size());
    }

    BalanceResponse response = library.balance(keysPath + "client5.pub");
    assertEquals(1000, response.getAmount(), "Initial balance should be 1000");

    List<Integer> sizes = new ArrayList<>();
    List<List<Block>> ledgers = new ArrayList<>();
    for (Node node : nodes) {
      sizes.add(node.getNodeService().getLedger().size());
      ledgers.add(node.getNodeService().getLedger());
    }

    long sizeCount = sizes.stream().filter(size -> size == 1).count();
    assertTrue(sizeCount >= f + 1, "At least f + 1 nodes should have ledgers of size " + 1);

    // TODO: compare ledgers
  }

}
