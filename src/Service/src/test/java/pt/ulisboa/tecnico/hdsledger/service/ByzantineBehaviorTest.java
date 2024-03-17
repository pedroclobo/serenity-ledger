package pt.ulisboa.tecnico.hdsledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pt.ulisboa.tecnico.hdsledger.library.Library;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

public abstract class ByzantineBehaviorTest {

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

  @Test
  void singleAppend() {
    for (Node node : nodes) {
      assertEquals(0, node.getNodeService().getLedger().size());
    }

    library.append("value");

    List<Integer> sizes = new ArrayList<>();
    List<String> values = new ArrayList<>();
    for (Node node : nodes) {
      sizes.add(node.getNodeService().getLedger().size());
      values.add(String.join("", node.getNodeService().getLedger()));
    }

    long sizeCount = sizes.stream().filter(size -> size == 1).count();
    assertTrue(sizeCount >= f + 1, "At least f + 1 nodes should have ledgers of size " + 1);

    long valueCount = values.stream().filter(value -> value.equals("value")).count();
    assertTrue(valueCount >= f + 1, "At least f + 1 nodes should have the correct ledger");
  }

  @Test
  public void multipleAppends() {
    for (Node node : nodes) {
      assertEquals(0, node.getNodeService().getLedger().size());
    }

    library.append("value1");
    library.append("value2");
    library.append("value3");

    List<Integer> sizes = new ArrayList<>();
    List<String> values = new ArrayList<>();
    for (Node node : nodes) {
      sizes.add(node.getNodeService().getLedger().size());
      values.add(String.join("", node.getNodeService().getLedger()));
    }

    long sizeCount = sizes.stream().filter(size -> size == 3).count();
    assertTrue(sizeCount >= f + 1, "At least f + 1 nodes should have ledgers of size " + 3);

    long valueCount = values.stream().filter(value -> value.equals("value1value2value3")).count();
    assertTrue(valueCount >= f + 1, "At least f + 1 nodes should have the correct ledger");
  }

  @Test
  public void multipleConcurrentAppends() throws InterruptedException {
    Thread thread1 = new Thread(() -> library.append("value1"));
    Thread thread2 = new Thread(() -> library.append("value2"));

    thread1.start();
    thread2.start();
    thread1.join();
    thread2.join();

    List<Integer> sizes = new ArrayList<>();
    List<String> values = new ArrayList<>();
    for (Node node : nodes) {
      sizes.add(node.getNodeService().getLedger().size());
      values.add(String.join("", node.getNodeService().getLedger()));
    }

    long sizeCount = sizes.stream().filter(size -> size == 2).count();
    assertTrue(sizeCount >= f + 1, "At least f + 1 nodes should have ledgers of size " + 2);

    List<Long> valueCount =
        values.stream().collect(Collectors.groupingBy(s -> s, Collectors.counting())).values()
            .stream().collect(Collectors.toList());

    assertTrue(valueCount.stream().anyMatch(count -> count >= f + 1),
        "At least f + 1 nodes should have the correct ledger");
  }
}
