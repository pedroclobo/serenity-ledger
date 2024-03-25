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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.communication.application.BalanceRequest;
import pt.ulisboa.tecnico.hdsledger.communication.application.BalanceResponse;
import pt.ulisboa.tecnico.hdsledger.communication.application.ClientRequest;
import pt.ulisboa.tecnico.hdsledger.communication.application.ClientResponse;
import pt.ulisboa.tecnico.hdsledger.communication.application.TransferRequest;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;
import pt.ulisboa.tecnico.hdsledger.utilities.exceptions.InvalidSignatureException;

public class Library {

  private final HDSLogger logger;

  private int clientId;
  private ProcessConfig[] nodeConfigs;
  private ProcessConfig[] clientConfigs;
  private ProcessConfig clientConfig;

  private int f;

  private Link link;

  private AtomicInteger nonce;

  private Map<Integer, List<ClientResponse>> responses;
  private Map<Integer, CountDownLatch> latches;

  public Library(int clientId, ProcessConfig[] nodeConfigs, ProcessConfig[] clientConfigs,
      boolean debug) {
    this.logger = new HDSLogger(Library.class.getName(), debug);

    this.clientId = clientId;
    this.nodeConfigs = nodeConfigs;
    this.clientConfigs = clientConfigs;
    this.clientConfig = clientConfigs[clientId - nodeConfigs.length - 1];

    this.f = (nodeConfigs.length - 1) / 3;

    this.link = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, ClientResponse.class);

    this.nonce = new AtomicInteger(0);

    this.responses = new ConcurrentHashMap<>();
    this.latches = new ConcurrentHashMap<>();

    listen();
  }

  public BalanceResponse balance(int sourceId) {
    // Client id
    if (sourceId > nodeConfigs.length) {
      return balance(clientConfigs[sourceId - nodeConfigs.length - 1].getPublicKeyPath());
    }

    // Node id
    return balance(nodeConfigs[sourceId - 1].getPublicKeyPath());
  }

  public BalanceResponse balance(String sourcePublicKeyPath) {
    // Grab nonce of the request
    int nonce = this.nonce.getAndIncrement();

    // Read source public key
    PublicKey sourcePublicKey;
    try {
      sourcePublicKey = RSACryptography.readPublicKey(sourcePublicKeyPath);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      throw new HDSException(ErrorMessage.ErrorReadingPublicKey);
    }

    // Sign balance request
    BalanceRequest balanceMessage = new BalanceRequest(nonce, sourcePublicKey);
    String serializedBalanceMessage = new Gson().toJson(balanceMessage);
    String signature;
    try {
      PrivateKey privateKey = RSACryptography.readPrivateKey(clientConfig.getPrivateKeyPath());
      signature = RSACryptography.sign(serializedBalanceMessage, privateKey);
    } catch (Exception e) {
      throw new HDSException(ErrorMessage.SigningError);
    }

    // Create latch and response vector for this request
    this.responses.put(nonce, new ArrayList<>());
    this.latches.put(nonce, new CountDownLatch(1));

    // Broadcast balance request
    logger.info(MessageFormat.format("[{0}] - Broadcasting balance request for {1} with nonce {2}",
        clientConfig.getId(), sourcePublicKeyPath, nonce));
    ClientRequest message = new ClientRequest(clientConfig.getId(), Type.BALANCE_REQUEST,
        serializedBalanceMessage, signature);
    link.broadcast(message);

    // Wait for f + 1 responses
    try {
      this.latches.get(nonce).await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    // Grab the first response
    BalanceResponse response = this.responses.get(nonce).get(0).deserializeBalanceRequest();

    // Clean up
    this.responses.remove(nonce);
    this.latches.remove(nonce);

    return response;
  }

  public void transfer(int sourceId, int destinationId, int amount) {
    transfer(clientConfigs[sourceId - nodeConfigs.length - 1].getPublicKeyPath(),
        clientConfigs[destinationId - nodeConfigs.length - 1].getPublicKeyPath(), amount);
  }

  public void transfer(String sourcePublicKeyPath, String destinationPublicKeyPath, int amount) {
    // Grab nonce of the request
    int nonce = this.nonce.getAndIncrement();

    // Verify that amount is positive
    if (amount <= 0) {
      throw new HDSException(ErrorMessage.InvalidAmount);
    }

    // Read source public key
    PublicKey sourcePublicKey;
    try {
      sourcePublicKey = RSACryptography.readPublicKey(sourcePublicKeyPath);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      throw new HDSException(ErrorMessage.ErrorReadingPublicKey);
    }

    // Read destination public key
    PublicKey destinationPublicKey;
    try {
      destinationPublicKey = RSACryptography.readPublicKey(destinationPublicKeyPath);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      throw new HDSException(ErrorMessage.ErrorReadingPublicKey);
    }

    // Create latch and response vector for this request
    this.responses.put(nonce, new ArrayList<>());
    this.latches.put(nonce, new CountDownLatch(1));

    // Sign transfer request
    TransferRequest transferRequest =
        new TransferRequest(nonce, sourcePublicKey, destinationPublicKey, amount);
    String serializedTransferMessage = new Gson().toJson(transferRequest);
    String signature;
    try {
      PrivateKey privateKey = RSACryptography.readPrivateKey(clientConfig.getPrivateKeyPath());
      signature = RSACryptography.sign(serializedTransferMessage, privateKey);
    } catch (Exception e) {
      throw new HDSException(ErrorMessage.SigningError);
    }

    // Broadcast transfer request
    logger.info(MessageFormat.format(
        "[{0}] - Broadcasting transfer request from {1} to {2} with amount {3} and nonce {4}",
        clientConfig.getId(), sourcePublicKeyPath, destinationPublicKeyPath, amount, nonce));
    ClientRequest message = new ClientRequest(clientConfig.getId(), Type.TRANSFER_REQUEST,
        serializedTransferMessage, signature);
    link.broadcast(message);

    // Wait for f + 1 responses
    try {
      this.latches.get(nonce).await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    // Clean up
    this.responses.remove(nonce);
    this.latches.remove(nonce);
  }

  private void uponBalanceResponse(ClientResponse response) {
    int senderId = response.getSenderId();
    int nonce = response.deserializeBalanceRequest().getNonce();

    // The balance was already confirmed
    if (!this.responses.containsKey(nonce)) {
      logger.info(MessageFormat.format(
          "[{0}] - Balance response with nonce {1} from {2} for already confirmed balance",
          clientConfig.getId(), nonce, senderId));
      return;
    }

    // Verify if the senderId is already in the responses
    if (this.responses.get(nonce).stream().anyMatch(r -> r.getSenderId() == senderId)) {
      logger.info(MessageFormat.format("[{0}] - Duplicate balance response from {1}",
          clientConfig.getId(), senderId));
      return;
    }

    logger.info(MessageFormat.format("[{0}] - Received balance response from {1}",
        clientConfig.getId(), response.getSenderId()));

    // Add message to responses
    this.responses.get(nonce).add(response);

    // TODO: verify that all messages are the same
    // There are enough responses and all have the same amount
    if (this.responses.get(nonce).size() > f && this.responses.get(nonce).stream()
        .map(x -> x.deserializeBalanceRequest().getAmount()).distinct().count() == 1) {
      logger.info(
          MessageFormat.format("[{0}] - Received enough balance responses", clientConfig.getId()));
      synchronized (this.latches.get(nonce)) {
        this.latches.get(nonce).countDown();
      }
    }
  }

  private void uponTransferResponse(ClientResponse response) {
    int senderId = response.getSenderId();
    int nonce = response.deserializeTransferRequest().getNonce();

    // The transfer was already confirmed
    if (!this.responses.containsKey(nonce)) {
      logger.info(
          MessageFormat.format("[{0}] - Transfer response from {1} for already confirmed transfer",
              clientConfig.getId(), senderId));
      return;
    }

    // Verify if the senderId is already in the responses
    if (this.responses.get(nonce).stream().anyMatch(r -> r.getSenderId() == senderId)) {
      logger.info(MessageFormat.format("[{0}] - Duplicate transfer response from {1}",
          clientConfig.getId(), senderId));
      return;
    }

    logger.info(MessageFormat.format("[{0}] - Received transfer response from {1}",
        clientConfig.getId(), response.getSenderId()));

    // Add message to responses
    this.responses.get(nonce).add(response);

    // TODO: verify that all messages are the same
    // There are enough responses
    if (this.responses.get(nonce).size() > f) {
      logger.info(
          MessageFormat.format("[{0}] - Received enough transfer responses", clientConfig.getId()));
      synchronized (this.latches.get(nonce)) {
        this.latches.get(nonce).countDown();
      }
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
              case BALANCE_RESPONSE -> {
                uponBalanceResponse((ClientResponse) message);
                break;
              }
              case TRANSFER_RESPONSE -> {
                uponTransferResponse((ClientResponse) message);
                break;
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
        } catch (HDSException e) {
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
