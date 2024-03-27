package pt.ulisboa.tecnico.hdsledger.service.models;

public class Account {

  private int ownerId;
  // Hash of public key
  private String publicKeyHash;
  private int balance;

  public Account(int ownerId, String publicKeyHash) {
    this.ownerId = ownerId;
    this.publicKeyHash = publicKeyHash;
    this.balance = 1000;
  }

  public int getOwnerId() {
    return ownerId;
  }

  public String getPublicKeyHash() {
    return publicKeyHash;
  }

  public int getBalance() {
    return balance;
  }

  public void addBalance(int amount) {
    this.balance += amount;
  }

  public boolean subtractBalance(int amount) {
    if (this.balance < amount) {
      return false;
    }

    this.balance -= amount;

    return true;
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
    if (!(obj instanceof Account)) {
      return false;
    }
    final Account other = (Account) obj;

    return ownerId == other.ownerId && publicKeyHash.equals(other.publicKeyHash)
        && balance == other.balance;
  }

}
