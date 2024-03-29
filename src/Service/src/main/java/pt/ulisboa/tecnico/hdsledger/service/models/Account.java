package pt.ulisboa.tecnico.hdsledger.service.models;

public class Account {

  private final int ownerId;
  // Hash of public key
  private final String publicKeyHash;
  private int balance;

  public Account(int ownerId, String publicKeyHash) {
    this.ownerId = ownerId;
    this.publicKeyHash = publicKeyHash;
    this.balance = 1000;
  }

  public int getOwnerId() {
    return ownerId;
  }

  public int getBalance() {
    return balance;
  }

  public void addBalance(int amount) {
    this.balance += amount;
  }

  public void subtractBalance(int amount) {
    if (this.balance < amount) {
      return;
    }

    this.balance -= amount;
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
    if (!(obj instanceof Account other)) {
      return false;
    }

    return ownerId == other.ownerId && publicKeyHash.equals(other.publicKeyHash)
        && balance == other.balance;
  }

}
