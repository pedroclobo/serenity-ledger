package pt.ulisboa.tecnico.hdsledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pt.ulisboa.tecnico.hdsledger.communication.application.BalanceRequest;
import pt.ulisboa.tecnico.hdsledger.communication.application.BalanceResponse;
import pt.ulisboa.tecnico.hdsledger.communication.application.TransferResponse;
import pt.ulisboa.tecnico.hdsledger.library.Library;
import pt.ulisboa.tecnico.hdsledger.service.models.Ledger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

public abstract class ByzantineBehaviorTest {

  private static int BLOCK_SIZE = 1;

  private String nodesConfigPath = "src/main/resources/";
  private String clientsConfigPath = "../Client/src/main/resources/";

  protected List<Node> nodes;
  protected List<Library> libraries;

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
    libraries = new ArrayList<>();

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

    for (ProcessConfig clientConfig : newClientsConfig) {
      int clientId = clientConfig.getId();
      libraries.add(new Library(clientId, newNodesConfig, newClientsConfig, false));
    }
  }

  @AfterEach
  public final void tearDown() {
    for (Node node : nodes) {
      node.shutdown();
    }
    for (Library library : libraries) {
      library.shutdown();
    }
  }

  @Test
  void transfersAndBalances() {
    for (Node node : nodes) {
      assertEquals(0, node.getNodeService().getLedger().getLedger().size());
    }

    BalanceResponse balanceResponse = libraries.get(0).balance(5);
    assertTrue(balanceResponse.isSuccessful(), "Balance request should be successful");
    assertEquals(1000, balanceResponse.getAmount().get(), "Initial balance should be 1000");

    balanceResponse = libraries.get(0).balance(6);
    assertTrue(balanceResponse.isSuccessful(), "Balance request should be successful");
    assertEquals(1000, balanceResponse.getAmount().get(), "Initial balance should be 1000");

    TransferResponse transferResponse = libraries.get(0).transfer(5, 6, 100);
    assertTrue(transferResponse.isSuccessful(), "Transfer request should be successful");

    balanceResponse = libraries.get(0).balance(5);
    assertTrue(balanceResponse.isSuccessful(), "Balance request should be successful");
    assertEquals(900, balanceResponse.getAmount().get(), "New balance should be 900");

    balanceResponse = libraries.get(0).balance(6);
    assertTrue(balanceResponse.isSuccessful(), "Balance request should be successful");
    assertEquals(1100, balanceResponse.getAmount().get(), "New balance should be 1100");

    List<Integer> sizes = new ArrayList<>();
    List<Ledger> ledgers = new ArrayList<>();
    for (Node node : nodes) {
      Ledger ledger = node.getNodeService().getLedger();
      sizes.add(ledger.getLedger().size());
      ledgers.add(ledger);
    }

    long sizeCount = sizes.stream().filter(size -> size == 5).count();
    assertTrue(sizeCount >= f + 1, "At least f + 1 nodes should have ledgers of size " + 5);

    List<Ledger> ledgersWithSizeFPlus1 =
        ledgers.stream().filter(ledger -> ledger.getLedger().size() == 5).toList();
    assertEquals(1, ledgersWithSizeFPlus1.stream().distinct().count(), "Ledgers should be equal");
  }
}
