package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.List;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.application.ClientRequest;

public class Block {

  private int consensusInstance;
  private List<ClientRequest> transactions;

  public Block() {
    transactions = List.of();
  }

  public int getConsensusInstance() {
    return consensusInstance;
  }

  public void setConsensusInstance(int consensusInstance) {
    this.consensusInstance = consensusInstance;
  }

  public void addTransaction(ClientRequest transaction) {
    transactions.add(transaction);
  }

  public List<ClientRequest> getTransactions() {
    return transactions;
  }

  public void setTransactions(List<ClientRequest> transactions) {
    this.transactions = transactions;
  }

  public static Block fromJson(String json) {
    return new Gson().fromJson(json, Block.class);
  }

  public String toJson() {
    return new Gson().toJson(this);
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof Block)) {
      return false;
    }
    final Block other = (Block) obj;

    return consensusInstance == other.consensusInstance && transactions.equals(other.transactions);
  }

}
