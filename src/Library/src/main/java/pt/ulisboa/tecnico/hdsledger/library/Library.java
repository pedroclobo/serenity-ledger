package pt.ulisboa.tecnico.hdsledger.library;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class Library {

  private static final Logger LOGGER = Logger.getLogger(Library.class.getName());
  private Link link;
  private final String id = "5";
  private ProcessConfig clientConfig;

  public Library(ProcessConfig[] nodeConfigs, ProcessConfig clientConfig, boolean activateLogs) {
    this.clientConfig = clientConfig;
    link = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, AppendMessage.class);
  }

  public void append(String value) {
    Message message = new AppendMessage(id, Type.APPEND, value);
    link.broadcast(message);
    listen();
  }

  public void listen() {
        try {
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();

                        switch (message.getType()) {
                            case APPEND -> {
                                LOGGER.log(Level.INFO, "{0} - Received APPEND message from {1}",
                                        new Object[]{id, message.getSenderId()});
                            }
                            case ACK -> {
                                LOGGER.log(Level.INFO, "{0} - Received ACK message from {1}",
                                        new Object[]{id, message.getSenderId()});
                                continue;
                            }
                            case IGNORE -> {
                                LOGGER.log(Level.INFO, "{0} - Received IGNORE message from {1}",
                                        new Object[]{id, message.getSenderId()});
                                continue;
                            }
                            default -> {
                                LOGGER.log(Level.INFO, "{0} - Received unknown message from {1}",
                                        new Object[]{id, message.getSenderId()});
                                continue;
                            }
                        }
                    }
                } catch (HDSSException e) {
                    LOGGER.log(Level.INFO, "{0} - EXCEPTION: {1}",
                            new Object[]{clientConfig.getId(), e.getMessage()});
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
