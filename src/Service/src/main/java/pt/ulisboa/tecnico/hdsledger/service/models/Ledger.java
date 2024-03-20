package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.ArrayList;
import java.util.List;

public class Ledger {

  private List<Block> ledger;

  public Ledger() {
    ledger = new ArrayList<>();
  }

  public List<Block> getLedger() {
    return ledger;
  }

  public void addValue(Block block) {
    synchronized (ledger) {
      ledger.add(block);
    }
  }
}
