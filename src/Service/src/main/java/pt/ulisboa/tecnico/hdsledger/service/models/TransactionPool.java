package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import pt.ulisboa.tecnico.hdsledger.communication.ClientRequest;

public class TransactionPool {

  private int blockSize;
  private List<ClientRequest> pool;

  public TransactionPool(int blockSize) {
    this.blockSize = blockSize;
    this.pool = new ArrayList<>();
  }

  public void addTransaction(ClientRequest transaction) {
    synchronized (pool) {
      pool.add(transaction);
    }
  }

  /*
   * Returns a block if the pool has enough transactions to create a block
   */
  public Optional<Block> getBlock() {
    synchronized (pool) {
      if (pool.size() >= blockSize) {
        Block block = new Block();
        block.setTransactions(pool);
        pool = new ArrayList<>();
        return Optional.of(block);
      }

      return Optional.empty();
    }
  }

}
