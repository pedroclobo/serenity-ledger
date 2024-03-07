package pt.ulisboa.tecnico.hdsledger.utilities;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class CustomLogger {

  private final Logger logger;
  private boolean activateLogs;

  public CustomLogger(String name, boolean activateLogs) {
    this.activateLogs = activateLogs;
    this.logger = Logger.getLogger(name);
    logger.setUseParentHandlers(false);
    ConsoleHandler handler = new ConsoleHandler();
    handler.setFormatter(new CustomLog());
    logger.addHandler(handler);
  }

  public void log(String message) {
    if (!activateLogs)
      return;
    logger.log(Level.INFO, message);
  }

  public void deactivateLogs() {
    activateLogs = false;
  }
}


class CustomLog extends Formatter {
  @Override
  public String format(LogRecord record) {
    StringBuilder sb = new StringBuilder();
    sb.append(record.getMessage()).append('\n');
    return sb.toString();
  }
}
