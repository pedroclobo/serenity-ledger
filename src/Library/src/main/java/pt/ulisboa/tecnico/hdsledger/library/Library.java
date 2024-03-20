package pt.ulisboa.tecnico.hdsledger.library;

import java.io.IOException;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.MessageFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.BalanceMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ClientMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;
import pt.ulisboa.tecnico.hdsledger.utilities.exceptions.InvalidSignatureException;

public class Library {

  private final HDSLogger logger;

  private Link link;
  private ProcessConfig clientConfig;
  private ConcurrentHashMap<String, CountDownLatch> acks;

  public Library(ProcessConfig[] nodeConfigs, ProcessConfig clientConfig, boolean debug) {
    this.logger = new HDSLogger(Library.class.getName(), debug);
    link = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, ClientMessage.class);
    this.clientConfig = clientConfig;
    this.acks = new ConcurrentHashMap<>();
  }

  public void balance(String sourcePublicKeyPath) {
    // Read source public key
    PublicKey sourcePublicKey;
    try {
      sourcePublicKey = RSACryptography.readPublicKey(sourcePublicKeyPath);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      throw new HDSSException(ErrorMessage.ErrorReadingPublicKey);
    }

    // Sign balance request
    BalanceMessage balanceMessage = new BalanceMessage(sourcePublicKey);
    String serializedBalanceMessage = new Gson().toJson(balanceMessage);
    String signature;
    try {
      PrivateKey privateKey = RSACryptography.readPrivateKey(clientConfig.getPrivateKeyPath());
      signature = RSACryptography.sign(serializedBalanceMessage, privateKey);
    } catch (Exception e) {
      throw new HDSSException(ErrorMessage.SigningError);
    }

    // Broadcast balance request
    ClientMessage message =
        new ClientMessage(clientConfig.getId(), Type.BALANCE, serializedBalanceMessage, signature);
    link.broadcast(message);
  }

  public void transfer(String sourcePublicKeyPath, String destinationPublicKeyPath, int amount) {
    throw new UnsupportedOperationException("Unimplemented method 'transfer'");
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
