package pt.ulisboa.tecnico.hdsledger.library;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.exceptions.InvalidSignatureException;

public class Library {

  private static final Logger LOGGER = Logger.getLogger(Library.class.getName());

  private Link link;
  private ProcessConfig clientConfig;
  private int quorumSize;
  private ConcurrentHashMap<String, Integer> acks;

  public Library(ProcessConfig[] nodeConfigs, ProcessConfig clientConfig, boolean activateLogs) {
    link = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, AppendMessage.class);
    this.clientConfig = clientConfig;
    this.quorumSize = (int) Math.floor((nodeConfigs.length + 1) / 2) + 1;
    this.acks = new ConcurrentHashMap<>();
  }

  public void append(String value) {
    Message message = new AppendMessage(clientConfig.getId(), Type.APPEND, value);
    acks.put(value, 0);
    link.broadcast(message);

    // Wait for the reply of a quorum of nodes
    Thread thread = new Thread(() -> {
      while (acks.get(value) < quorumSize) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      acks.remove(value);
    });

    thread.start();
    listen();

    try {
      thread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public void listen() {
    try {
      new Thread(() -> {
        try {
          while (true) {
            Message message;

            try {
              message = link.receive();
            } catch (InvalidSignatureException e) {
              LOGGER.log(Level.INFO, "{0} - EXCEPTION: {1}",
                  new Object[] {clientConfig.getId(), e.getMessage()});
              continue;
            }

            switch (message.getType()) {
              case APPEND -> {
                AppendMessage appendMessage = (AppendMessage) message;
                synchronized (acks) {
                  acks.put(appendMessage.getValue(),
                      acks.getOrDefault(appendMessage.getValue(), 0) + 1);
                }
                LOGGER.log(Level.INFO, "{0} - Received APPEND message from {1}",
                    new Object[] {clientConfig.getId(), message.getSenderId()});
              }
              case ACK -> {
                LOGGER.log(Level.INFO, "{0} - Received ACK message from {1}",
                    new Object[] {clientConfig.getId(), message.getSenderId()});
                continue;
              }
              case IGNORE -> {
                LOGGER.log(Level.INFO, "{0} - Received IGNORE message from {1}",
                    new Object[] {clientConfig.getId(), message.getSenderId()});
                continue;
              }
              default -> {
                LOGGER.log(Level.INFO, "{0} - Received unknown message from {1}",
                    new Object[] {clientConfig.getId(), message.getSenderId()});
                continue;
              }
            }
          }
        } catch (HDSSException e) {
          LOGGER.log(Level.INFO, "{0} - EXCEPTION: {1}",
              new Object[] {clientConfig.getId(), e.getMessage()});
        } catch (IOException | ClassNotFoundException e) {
          e.printStackTrace();
        }
      }).start();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
