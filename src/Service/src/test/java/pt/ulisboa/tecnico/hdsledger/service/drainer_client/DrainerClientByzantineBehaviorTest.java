package pt.ulisboa.tecnico.hdsledger.service.drainer_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pt.ulisboa.tecnico.hdsledger.communication.application.BalanceResponse;
import pt.ulisboa.tecnico.hdsledger.communication.application.TransferResponse;
import pt.ulisboa.tecnico.hdsledger.service.ByzantineBehaviorTest;
import pt.ulisboa.tecnico.hdsledger.service.Node;
import pt.ulisboa.tecnico.hdsledger.service.models.Ledger;

class DrainerClientByzantineBehaviorTest extends ByzantineBehaviorTest {

  public DrainerClientByzantineBehaviorTest() {
    super("drainer_client/nodes.json", "drainer_client/clients.json");
  }

  @Test
  void transfersAndBalances() {
    for (Node node : nodes) {
      assertEquals(0, node.getNodeService().getLedger().getLedger().size());
    }

    TransferResponse transferResponse = libraries.get(0).transfer(5, 6, 100);
    assertFalse(transferResponse.isSuccessful(), "Transfer request should be unsuccessful");

    BalanceResponse balanceResponse = libraries.get(0).balance(5);
    assertTrue(balanceResponse.isSuccessful(), "Balance request should be successful");
    assertEquals(1000, balanceResponse.getAmount().get(), "Initial balance should be 1000");

    balanceResponse = libraries.get(0).balance(6);
    assertTrue(balanceResponse.isSuccessful(), "Balance request should be successful");
    assertEquals(1000, balanceResponse.getAmount().get(), "Initial balance should be 1000");

    List<Integer> sizes = new ArrayList<>();
    List<Ledger> ledgers = new ArrayList<>();
    for (Node node : nodes) {
      Ledger ledger = node.getNodeService().getLedger();
      sizes.add(ledger.getLedger().size());
      ledgers.add(ledger);
    }

    int maxSize = sizes.stream().mapToInt(Integer::intValue).max().getAsInt();
    long sizeCount = sizes.stream().filter(size -> size == maxSize).count();
    assertTrue(sizeCount >= f + 1, "At least f + 1 nodes should have ledgers of size " + maxSize);

    List<Ledger> ledgersWithSizeFPlus1 =
        ledgers.stream().filter(ledger -> ledger.getLedger().size() == maxSize).toList();
    assertEquals(1, ledgersWithSizeFPlus1.stream().distinct().count(), "Ledgers should be equal");
  }

}
