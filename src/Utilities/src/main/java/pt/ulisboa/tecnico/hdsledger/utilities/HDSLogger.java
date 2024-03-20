package pt.ulisboa.tecnico.hdsledger.utilities;

import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class HDSLogger {
  private Logger logger;
  private boolean debug;

  public HDSLogger(String name, boolean debug) {
    this.debug = debug;
    this.logger = Logger.getLogger(name);
    logger.setUseParentHandlers(false);
    ConsoleHandler handler = new ConsoleHandler();
    handler.setFormatter(new HDSLogFormatter());
    logger.addHandler(handler);

    try {
      FileHandler fileHandler = new FileHandler("log", true);
      fileHandler.setFormatter(new HDSLogFormatter());
      logger.addHandler(fileHandler);
    } catch (Exception e) {
    }
  }

  public void info(String message) {
    if (!debug)
      return;
    logger.log(Level.INFO, message);
  }
}


class HDSLogFormatter extends Formatter {
  @Override
  public String format(LogRecord record) {
    StringBuilder sb = new StringBuilder();
    sb.append(record.getMessage()).append('\n');
    return sb.toString();
  }
}
