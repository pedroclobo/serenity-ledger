package pt.ulisboa.tecnico.hdsledger.utilities;

import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class HDSLogger {
  private final Logger logger;
  private final boolean debug;

  public HDSLogger(String name, boolean debug) {
    this.debug = debug;
    this.logger = Logger.getLogger(name);
    logger.setUseParentHandlers(false);
    ConsoleHandler handler = new ConsoleHandler();
    handler.setFormatter(new HDSLogFormatter());
    logger.addHandler(handler);
  }

  public void info(String message) {
    if (!debug)
      return;

    // Lazy initialization of log file
    if (logger.getHandlers().length <= 1) {
      try {
        FileHandler fileHandler = new FileHandler("src/main/resources/hds.log", false);
        fileHandler.setFormatter(new HDSLogFormatter());
        logger.addHandler(fileHandler);
      } catch (Exception e) {
        System.err.println("Failed to create log file");
      }
    }

    logger.log(Level.INFO, message);
  }
}


class HDSLogFormatter extends Formatter {
  @Override
  public String format(LogRecord record) {
    return record.getMessage() + '\n';
  }
}
