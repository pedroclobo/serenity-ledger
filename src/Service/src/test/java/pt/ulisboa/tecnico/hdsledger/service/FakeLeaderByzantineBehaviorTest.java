package pt.ulisboa.tecnico.hdsledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
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
  @Disabled
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
