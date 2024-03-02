package pt.ulisboa.tecnico.hdsledger.library;

import java.io.IOException;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

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
  private ConcurrentHashMap<String, CountDownLatch> acks;

  public Library(ProcessConfig[] nodeConfigs, ProcessConfig clientConfig, boolean activateLogs) {
    link = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, AppendMessage.class);
    this.clientConfig = clientConfig;
    this.quorumSize = (int) Math.floor((nodeConfigs.length + 1) / 2) + 1;
    this.acks = new ConcurrentHashMap<>();
  }

  public void append(String value) {
    Message message = new AppendMessage(clientConfig.getId(), Type.APPEND, value);
    CountDownLatch latch = new CountDownLatch(quorumSize);

    synchronized (acks) {
      acks.put(value, latch);
    }

    link.broadcast(message);
    listen();

    // Wait for the reply of a quorum of nodes
    try {
      latch.await();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    synchronized (acks) {
      acks.remove(value);
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
                if (acks.containsKey(appendMessage.getValue())) {
                  acks.get(appendMessage.getValue()).countDown();
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
        } catch (SocketException e) {
          // Supress message during shutdown
        } catch (IOException | ClassNotFoundException e) {
          e.printStackTrace();
        }
      }).start();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void shutdown() {
    link.shutdown();
  }
}
