package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.List;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.ClientRequest;

public class Block {

  private int consensusInstance;
  private List<ClientRequest> transactions;

  public Block() {}

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

}
