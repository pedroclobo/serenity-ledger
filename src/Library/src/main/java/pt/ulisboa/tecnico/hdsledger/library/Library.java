package pt.ulisboa.tecnico.hdsledger.library;

import java.io.IOException;
import java.net.SocketException;
import java.text.MessageFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.Arrays;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.exceptions.InvalidSignatureException;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;

public class Library {

  private static String clientsConfigPath = "../Client/src/main/resources/";
  private static String nodesConfigPath = "../Service/src/main/resources/";

  private final CustomLogger logger;

  private Link link;
  private ProcessConfig clientConfig;
  private int quorumSize;
  private ConcurrentHashMap<String, CountDownLatch> acks;

  public static void main(String[] args) throws InterruptedException {

    // Parse command line arguments
    if (args.length != 3 && args.length != 4) {
      System.err.println(
          "Usage: java Library <clientId> <nodesConfigPath> <clientsConfigPath> [--verbose|-v]");
      System.exit(1);
    }

    int clientId = Integer.parseInt(args[0]);
    nodesConfigPath += args[1];
    clientsConfigPath += args[2];
    boolean activateLogs = false;
    if (args.length == 4) {
      // Activate logs
      activateLogs = args[3].equals("--verbose") || args[3].equals("-v");
    }

    // Retrieve client and node configurations from files
    ProcessConfig[] clientConfigs = ProcessConfigBuilder.fromFile(clientsConfigPath);
    ProcessConfig[] nodeConfigs = ProcessConfigBuilder.fromFile(nodesConfigPath);

    // The client connects to the server using the server's specified client port
    for (ProcessConfig nodeConfig : nodeConfigs) {
      nodeConfig.setPort(nodeConfig.getClientPort());
    }

    // Retrieve the current client's config
    ProcessConfig clientConfig = Arrays.stream(clientConfigs).filter(c -> c.getId() == clientId)
        .findFirst().orElseThrow(() -> new HDSSException(ErrorMessage.ConfigFileNotFound));

    // The library is responsible for translating client's requests into
    // messages and sending them to the server
    Library library = new Library(nodeConfigs, clientConfig, activateLogs);

    Thread t1 = new Thread(() -> library.append("value1"));
    Thread t2 = new Thread(() -> library.append("value2"));
    Thread t3 = new Thread(() -> library.append("value3"));
    Thread t4 = new Thread(() -> library.append("value4"));
    Thread t5 = new Thread(() -> library.append("value5"));

    for (Thread t : new Thread[] {t1, t2, t3, t4, t5}) {
      t.start();
    }
    for (Thread t : new Thread[] {t1, t2, t3, t4, t5}) {
      t.join();
    }
  }

  public Library(ProcessConfig[] nodeConfigs, ProcessConfig clientConfig, boolean activateLogs) {
    link = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, AppendMessage.class);
    this.clientConfig = clientConfig;
    this.quorumSize = (int) Math.floor((nodeConfigs.length + 1) / 2) + 1;
    this.acks = new ConcurrentHashMap<>();
    this.logger = new CustomLogger(Library.class.getName(), activateLogs);
  }

  public void append(String value) {
    Message message = new AppendMessage(clientConfig.getId(), Type.APPEND, value);
    CountDownLatch latch = new CountDownLatch(quorumSize);

    synchronized (acks) {
      acks.put(value, latch);
    }

    link.smallMulticast(message);
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
              logger.log(MessageFormat.format("{0} - EXCEPTION: {1}", clientConfig.getId(),
                  e.getMessage()));
              continue;
            }

            switch (message.getType()) {
              case APPEND -> {
                AppendMessage appendMessage = (AppendMessage) message;
                if (acks.containsKey(appendMessage.getValue())) {
                  acks.get(appendMessage.getValue()).countDown();
                }
                logger.log(MessageFormat.format("{0} - Received APPEND message from {1}",
                    clientConfig.getId(), message.getSenderId()));
              }
              case ACK -> {
                logger.log(MessageFormat.format("{0} - Received ACK message from {1}",
                    clientConfig.getId(), message.getSenderId()));
                continue;
              }
              case IGNORE -> {
                logger.log(MessageFormat.format("{0} - Received IGNORE message from {1}",
                    clientConfig.getId(), message.getSenderId()));
                continue;
              }
              default -> {
                logger.log(MessageFormat.format("{0} - Received unknown message from {1}",
                    clientConfig.getId(), message.getSenderId()));
                continue;
              }
            }
          }
        } catch (HDSSException e) {
          logger.log(
              MessageFormat.format("{0} - EXCEPTION: {1}", clientConfig.getId(), e.getMessage()));
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
