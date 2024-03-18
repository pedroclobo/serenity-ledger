package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.ArrayList;
import java.util.List;

public class Ledger {

  private List<String> ledger;

  public Ledger() {
    ledger = new ArrayList<>();
  }

  public List<String> getLedger() {
    return ledger;
  }

  public void addValue(String value) {
    synchronized (ledger) {
      ledger.add(value);
    }
  }
}
