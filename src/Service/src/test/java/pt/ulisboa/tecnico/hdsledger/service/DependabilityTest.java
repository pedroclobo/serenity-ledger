package pt.ulisboa.tecnico.hdsledger.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import pt.ulisboa.tecnico.hdsledger.library.Library;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

class DependabilityTest {

  private static int BLOCK_SIZE = 1;

  private String nodesConfigPath = "src/main/resources/none/nodes.json";
  private String clientsConfigPath = "../Client/src/main/resources/none/clients.json";

  protected List<Node> nodes;
  protected List<Library> libraries;

  protected int f;
  protected int quorumSize;

  public DependabilityTest() {
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
  void checkBalanceOfNonExistingAccount() {
    assertThrows(HDSException.class, () -> {
      libraries.get(0).balance(7);
    });
  }

  @Test
  void transferFromNonExistingAccount() {
    assertThrows(HDSException.class, () -> {
      libraries.get(0).transfer(7, 6, 100);
    });
  }

  @Test
  void transferFromNodeAccount() {
    assertThrows(HDSException.class, () -> {
      libraries.get(0).transfer(1, 6, 100);
    });
  }

  @Test
  void transferToNonExistingAccount() {
    assertThrows(HDSException.class, () -> {
      libraries.get(0).transfer(5, 7, 100);
    });
  }

  @Test
  void transferNegativeAmount() {
    assertThrows(HDSException.class, () -> {
      libraries.get(0).transfer(5, 6, -100);
    });
  }

  @Test
  void transferFromOtherAccount() {
    assertThrows(HDSException.class, () -> {
      libraries.get(1).transfer(5, 6, 100);
    });
  }

}
