package pt.ulisboa.tecnico.hdsledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class FakeLeaderByzantineBehaviorTest extends ByzantineBehaviorTest {

  public FakeLeaderByzantineBehaviorTest() {
    super("fake_leader_config.json", "client_config.json");
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
    assertTrue(sizeCount >= quorumSize, "At least a quorum should have ledgers of size " + 1);

    long valueCount = values.stream().filter(value -> value.equals("value")).count();
    assertTrue(valueCount >= quorumSize, "At least a quorum should have the correct ledger");
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
    assertTrue(sizeCount >= quorumSize, "At least a quorum should have ledgers of size " + 3);

    long valueCount = values.stream().filter(value -> value.equals("value1value2value3")).count();
    assertTrue(valueCount >= quorumSize, "At least a quorum should have the correct ledger");
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
    assertTrue(sizeCount >= quorumSize, "At least a quorum should have ledgers of size " + 2);

    List<Long> valueCount =
        values.stream().collect(Collectors.groupingBy(s -> s, Collectors.counting())).values()
            .stream().collect(Collectors.toList());

    assertTrue(valueCount.stream().anyMatch(count -> count >= quorumSize),
        "At least a quorum should have the correct ledger");
  }

}
