package pt.ulisboa.tecnico.hdsledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pt.ulisboa.tecnico.hdsledger.library.Library;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

class SimpleConsensusTest {

  private String nodesConfigJson = String.join(",\n",
      "[{ \"id\": \"1\", \"isLeader\": true, \"hostname\": \"localhost\", \"port\": 3001, \"clientPort\": 4001 }",
      "{ \"id\": \"2\", \"isLeader\": false, \"hostname\": \"localhost\", \"port\": 3002, \"clientPort\": 4002 }",
      "{ \"id\": \"3\", \"isLeader\": false, \"hostname\": \"localhost\", \"port\": 3003, \"clientPort\": 4003 }",
      "{ \"id\": \"4\", \"isLeader\": false, \"hostname\": \"localhost\", \"port\": 3004, \"clientPort\": 4004 }]");

  private String clientsConfigJson =
      String.join(",\n", "[{ \"id\": \"5\", \"hostname\": \"localhost\", \"port\": 3005 }]");

  private ArrayList<Node> nodes;
  private Library library;

  private ProcessConfig[] parseConfigs(String nodesConfig) {
    return ProcessConfigBuilder.fromJson(nodesConfig);
  }

  @BeforeEach
  public void setUp() {
    nodes = new ArrayList<>();

    ProcessConfig[] nodesConfig = parseConfigs(nodesConfigJson);
    ProcessConfig[] clientsConfig = parseConfigs(clientsConfigJson);
    for (ProcessConfig nodeConfig : nodesConfig) {
      Node node = new Node(nodeConfig.getId(), nodesConfig, clientsConfig);
      nodes.add(node);
      node.start();
    }

    // Make the library use the client port
    // A new array has to be created or deep copy the original
    ProcessConfig[] newNodesConfig = parseConfigs(nodesConfigJson);
    ProcessConfig[] newClientsConfig = parseConfigs(clientsConfigJson);
    for (ProcessConfig nodeConfig : newNodesConfig) {
      nodeConfig.setPort(nodeConfig.getClientPort());
    }
    library = new Library(newNodesConfig, newClientsConfig[0], true);
  }

  @AfterEach
  public void tearDown() {
    for (Node node : nodes) {
      node.shutdown();
    }
    library.shutdown();
  }

  @Test
  void singleAppend() {
    for (Node node : nodes) {
      assertEquals(0, node.getNodeService().getLedger().size());
    }

    library.append("value");

    for (Node node : nodes) {
      assertEquals(1, node.getNodeService().getLedger().size());
      assertEquals("value", node.getNodeService().getLedger().get(0));
    }
  }

  @Test
  public void multipleAppends() {
    for (Node node : nodes) {
      assertEquals(0, node.getNodeService().getLedger().size());
    }

    library.append("value1");
    library.append("value2");
    library.append("value3");

    for (Node node : nodes) {
      assertEquals(3, node.getNodeService().getLedger().size());
      assertEquals("value1", node.getNodeService().getLedger().get(0));
      assertEquals("value2", node.getNodeService().getLedger().get(1));
      assertEquals("value3", node.getNodeService().getLedger().get(2));
    }
  }

  @Test
  public void multipleConcurrentAppends() throws InterruptedException {
    Thread thread1 = new Thread(() -> library.append("value1"));
    Thread thread2 = new Thread(() -> library.append("value2"));

    thread1.start();
    thread2.start();
    thread1.join();
    thread2.join();

    for (Node node : nodes) {
      assertEquals(2, node.getNodeService().getLedger().size());
      assertTrue(node.getNodeService().getLedger().contains("value1"));
      assertTrue(node.getNodeService().getLedger().contains("value2"));
    }
  }

}
