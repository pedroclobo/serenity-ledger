package pt.ulisboa.tecnico.hdsledger.utilities;

public class HDSException extends RuntimeException {

  private final ErrorMessage errorMessage;

  public HDSException(ErrorMessage message) {
    errorMessage = message;
  }

  @Override
  public String getMessage() {
    return errorMessage.getMessage();
  }
}
