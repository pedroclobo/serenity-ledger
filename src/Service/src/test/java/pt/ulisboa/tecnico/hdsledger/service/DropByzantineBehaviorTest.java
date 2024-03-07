package pt.ulisboa.tecnico.hdsledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig.ByzantineBehavior;

class DropByzantineBehaviorTest extends ByzantineBehaviorTest {

  public DropByzantineBehaviorTest() {
    super("drop_config.json", "client_config.json");
  }

  @Test
  void singleAppend() {
    for (Node node : nodes) {
      assertEquals(0, node.getNodeService().getLedger().size());
    }

    library.append("value");

    for (Node node : nodes) {
      if (node.getByzantineBehavior() == ByzantineBehavior.Drop) {
        continue;
      }
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
    library.append("value4");
    library.append("value5");

    for (Node node : nodes) {
      if (node.getByzantineBehavior() == ByzantineBehavior.Drop) {
        continue;
      }
      assertEquals(5, node.getNodeService().getLedger().size());
      assertEquals("value1", node.getNodeService().getLedger().get(0));
      assertEquals("value2", node.getNodeService().getLedger().get(1));
      assertEquals("value3", node.getNodeService().getLedger().get(2));
      assertEquals("value4", node.getNodeService().getLedger().get(3));
      assertEquals("value5", node.getNodeService().getLedger().get(4));
    }
  }

  @Test
  @Disabled
  public void multipleConcurrentAppends() throws InterruptedException {
    Thread t1 = new Thread(() -> library.append("value1"));
    Thread t2 = new Thread(() -> library.append("value2"));
    Thread t3 = new Thread(() -> library.append("value3"));
    Thread t4 = new Thread(() -> library.append("value4"));
    Thread t5 = new Thread(() -> library.append("value5"));

    for (Thread t : new Thread[] {t1, t2, t3, t4, t5}) {
      t.start();
    }
    for (Thread t : new Thread[] {t1, t2, t3, t4, t5}) {
      t.join();
    }

    for (Node node : nodes) {
      if (node.getByzantineBehavior() == ByzantineBehavior.Drop) {
        continue;
      }
      assertEquals(5, node.getNodeService().getLedger().size());
      assertTrue(node.getNodeService().getLedger().contains("value1"));
      assertTrue(node.getNodeService().getLedger().contains("value2"));
      assertTrue(node.getNodeService().getLedger().contains("value3"));
      assertTrue(node.getNodeService().getLedger().contains("value4"));
      assertTrue(node.getNodeService().getLedger().contains("value5"));
    }
  }

}
