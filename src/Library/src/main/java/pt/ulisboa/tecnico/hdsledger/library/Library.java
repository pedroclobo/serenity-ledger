package pt.ulisboa.tecnico.hdsledger.library;

import java.io.IOException;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.TransferMessage;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.BalanceMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.exceptions.InvalidSignatureException;

public class Library {

  private final HDSLogger logger;

  private Link link;
  private ProcessConfig clientConfig;
  private int quorumSize;
  private ConcurrentHashMap<String, CountDownLatch> acks;

  public Library(ProcessConfig[] nodeConfigs, ProcessConfig clientConfig, boolean debug) {
    this.logger = new HDSLogger(Library.class.getName(), debug);
    link = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, AppendMessage.class);
    this.clientConfig = clientConfig;
    this.quorumSize = (int) Math.floor((nodeConfigs.length + 1) / 2) + 1;
    this.acks = new ConcurrentHashMap<>();
  }

  public void transfer(String sourcePublicKeyPath, String destinationPublicKeyPath, int amount) {
    Message message = new TransferMessage(clientConfig.getId(), sourcePublicKeyPath,
        destinationPublicKeyPath, amount);

    link.smallMulticast(message);

    // TODO: wait for acknowledgments
  }

  public void check_balance(String accountPublicKeyPath) {
    Message message = new BalanceMessage(clientConfig.getId(), accountPublicKeyPath);

    link.smallMulticast(message);

    // TODO: wait for acknowledgments
  }

  public void append(String value) {
    Message message = new AppendMessage(clientConfig.getId(), value);

    try {
      ((AppendMessage) message).signValue(clientConfig.getPrivateKeyPath());
    } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException
        | InvalidKeySpecException e) {
      throw new HDSSException(ErrorMessage.SigningError);
    }

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
              logger.info(MessageFormat.format("[{0}] - EXCEPTION: {1}", clientConfig.getId(),
                  e.getMessage()));
              continue;
            }

            switch (message.getType()) {
              case APPEND -> {
                AppendMessage appendMessage = (AppendMessage) message;
                if (acks.containsKey(appendMessage.getValue())) {
                  acks.get(appendMessage.getValue()).countDown();
                }
                logger.info(MessageFormat.format("[{0}] - Received APPEND message from {1}",
                    clientConfig.getId(), message.getSenderId()));
              }
              case ACK -> {
                logger.info(MessageFormat.format("[{0}] - Received ACK message from {1}",
                    clientConfig.getId(), message.getSenderId()));
                continue;
              }
              case IGNORE -> {
                logger.info(MessageFormat.format("[{0}] - Received IGNORE message from {1}",
                    clientConfig.getId(), message.getSenderId()));
                continue;
              }
              default -> {
                logger.info(MessageFormat.format("[{0}] - Received unknown message from {1}",
                    clientConfig.getId(), message.getSenderId()));
                continue;
              }
            }
          }
        } catch (HDSSException e) {
          logger.info(
              MessageFormat.format("[{0}] - EXCEPTION: {1}", clientConfig.getId(), e.getMessage()));
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
