package pt.ulisboa.tecnico.hdsledger.utilities;

public enum ErrorMessage {
  ConfigFileNotFound(
      "The configuration file is not available at the path supplied"), ConfigFileFormat(
          "The configuration file has wrong syntax"), NoSuchNode(
              "Can't send a message to a non existing node"), NoSuchClient(
                  "Couldn't find the specified client"), NoLeaderNode(
                      "No leader node available"), SocketSendingError(
                          "Error while sending message"), CannotOpenSocket(
                              "Error while opening socket"), SigningError(
                                  "Error while producing signature"), SignatureVerificationError(
                                      "Error while verifying signature"), InvalidSignature(
                                          "Invalid signature"), ErrorReadingPublicKey(
                                              "Error reading public key"), InvalidAmount(
                                                  "Invalid amount"), DigestError(
                                                      "Error while hashing"), InvalidTransferSource(
                                                          "Invalid source account to perform transfer");

  private final String message;

  ErrorMessage(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }
}
