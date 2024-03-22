package pt.ulisboa.tecnico.hdsledger.library;

import java.io.IOException;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.MessageFormat;
import java.util.concurrent.CountDownLatch;
import java.util.List;
import java.util.ArrayList;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.communication.application.BalanceRequest;
import pt.ulisboa.tecnico.hdsledger.communication.application.BalanceResponse;
import pt.ulisboa.tecnico.hdsledger.communication.application.ClientRequest;
import pt.ulisboa.tecnico.hdsledger.communication.application.ClientResponse;
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
  private CountDownLatch latch;

  private int f;
  private List<ClientResponse> responses;

  public Library(ProcessConfig[] nodeConfigs, ProcessConfig clientConfig, boolean debug) {
    this.logger = new HDSLogger(Library.class.getName(), debug);
    link = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, ClientResponse.class);
    this.clientConfig = clientConfig;
    this.latch = new CountDownLatch(1);

    this.f = (nodeConfigs.length - 1) / 3;
    this.responses = new ArrayList<>();

    listen();
  }

  public BalanceResponse balance(String sourcePublicKeyPath) {
    // Read source public key
    PublicKey sourcePublicKey;
    try {
      sourcePublicKey = RSACryptography.readPublicKey(sourcePublicKeyPath);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      throw new HDSSException(ErrorMessage.ErrorReadingPublicKey);
    }

    // Sign balance request
    BalanceRequest balanceMessage = new BalanceRequest(sourcePublicKey);
    String serializedBalanceMessage = new Gson().toJson(balanceMessage);
    String signature;
    try {
      PrivateKey privateKey = RSACryptography.readPrivateKey(clientConfig.getPrivateKeyPath());
      signature = RSACryptography.sign(serializedBalanceMessage, privateKey);
    } catch (Exception e) {
      throw new HDSSException(ErrorMessage.SigningError);
    }

    // Broadcast balance request
    ClientRequest message = new ClientRequest(clientConfig.getId(), Type.BALANCE_REQUEST,
        serializedBalanceMessage, signature);
    link.broadcast(message);

    // Wait for f + 1 responses
    synchronized (this.latch) {
      this.latch = new CountDownLatch(1);
    }
    try {
      latch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    BalanceResponse response = this.responses.get(0).deserializeBalanceRequest();

    // Reset response counter
    this.responses.clear();
    this.latch = new CountDownLatch(1);

    return response;
  }

  public void transfer(String sourcePublicKeyPath, String destinationPublicKeyPath, int amount) {
    throw new UnsupportedOperationException("Unimplemented method 'transfer'");
  }

  private void uponBalanceResponse(ClientResponse response) {
    int senderId = response.getSenderId();

    // Verify if the senderId is already in the responses
    if (this.responses.stream().anyMatch(r -> r.getSenderId() == senderId)) {
      return;
    }

    this.responses.add(response);

    // There are enough responses and all have the same amount
    if (this.responses.size() > f && this.responses.stream()
        .map(x -> x.deserializeBalanceRequest().getAmount()).distinct().count() == 1) {
      synchronized (this.latch) {
        this.latch.countDown();
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
