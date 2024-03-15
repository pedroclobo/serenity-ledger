package pt.ulisboa.tecnico.hdsledger.communication;

public class BalanceMessage extends Message {

  private String accountPublicKeyPath;

  public BalanceMessage(int senderId, String accountPublicKeyPath) {
    super(senderId, Message.Type.BALANCE);
    this.accountPublicKeyPath = accountPublicKeyPath;
  }

  public String getAccountPublicKeyPath() {
    return accountPublicKeyPath;
  }

}
