package pt.ulisboa.tecnico.hdsledger.communication;

public class TransferMessage extends Message {

  private String sourcePublicKeyPath;
  private String destinationPublicKeyPath;
  private int amount;

  public TransferMessage(int senderId, String sourcePublicKeyPath, String destinationPublicKeyPath,
      int amount) {
    super(senderId, Message.Type.TRANSFER);
    this.sourcePublicKeyPath = sourcePublicKeyPath;
    this.destinationPublicKeyPath = destinationPublicKeyPath;
    this.amount = amount;
  }

  public String getSourcePublicKeyPath() {
    return sourcePublicKeyPath;
  }

  public String getDestinationPublicKeyPath() {
    return destinationPublicKeyPath;
  }

  public int getAmount() {
    return amount;
  }

}
