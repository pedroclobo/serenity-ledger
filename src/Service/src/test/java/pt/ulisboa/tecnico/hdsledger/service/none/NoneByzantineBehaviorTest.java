package pt.ulisboa.tecnico.hdsledger.service.none;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pt.ulisboa.tecnico.hdsledger.communication.application.BalanceResponse;
import pt.ulisboa.tecnico.hdsledger.communication.application.TransferResponse;
import pt.ulisboa.tecnico.hdsledger.service.ByzantineBehaviorTest;
import pt.ulisboa.tecnico.hdsledger.service.Node;
import pt.ulisboa.tecnico.hdsledger.service.models.Ledger;

class NoneByzantineBehaviorTest extends ByzantineBehaviorTest {

  public NoneByzantineBehaviorTest() {
    super("none/nodes.json", "none/clients.json");
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
