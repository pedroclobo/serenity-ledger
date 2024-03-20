package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Optional;

import pt.ulisboa.tecnico.hdsledger.communication.ClientMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.service.models.TransactionPool;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;
import pt.ulisboa.tecnico.hdsledger.utilities.exceptions.InvalidSignatureException;

public class ClientService implements UDPService {

  private final HDSLogger logger;
  // Node configuration
  private final ProcessConfig config;
  // Client configurations
  private final ProcessConfig[] clientConfigs;
  // Link to communicate with nodes
  private final Link link;
  // Node service
  private final NodeService nodeService;
  // Transaction pool
  private final TransactionPool pool;

  public ClientService(Link link, ProcessConfig config, ProcessConfig[] clientConfigs,
      NodeService nodeService, TransactionPool pool, boolean debug) {

    this.logger = new HDSLogger(ClientService.class.getName(), debug);
    this.link = link;
    this.config = config;
    this.clientConfigs = clientConfigs;
    this.nodeService = nodeService;
    this.pool = pool;
  }

  public ProcessConfig getConfig() {
    return this.config;
  }

  private boolean verifyClientSignature(ClientMessage message) {

    // Find configuration of the sender
    Optional<ProcessConfig> clientConfig = Arrays.stream(this.clientConfigs)
        .filter(c -> c.getId() == message.getSenderId()).findFirst();

    if (clientConfig.isEmpty()) {
      throw new HDSSException(ErrorMessage.NoSuchClient);
    }

    // Verify client signature
    try {
      PublicKey publicKey = RSACryptography.readPublicKey(clientConfig.get().getPublicKeyPath());
      if (RSACryptography.verify(message.getMessage(), publicKey, message.getClientSignature())) {
        return true;
      }
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      throw new HDSSException(ErrorMessage.SignatureVerificationError);
    }

    logger.info(MessageFormat.format("Invalid client signature from {0}", message.getSenderId()));

    return false;
  }

  public void balance(ClientMessage message) {
    logger.info(MessageFormat.format("[{0}]: Received balance request from {1}",
        this.config.getId(), message.getSenderId()));

    if (!verifyClientSignature(message)) {
      logger.info(MessageFormat.format("[{0}]: Invalid signature for balance request",
          this.config.getId()));
      return;
    }

    logger.info(MessageFormat.format("[{0}]: Adding transaction to pool", this.config.getId()));
    pool.addTransaction(message);

    startConsensus();
  }

  public void transfer(ClientMessage message) {
    logger.info(MessageFormat.format("[{0}]: Received Transfer from {1}", this.config.getId(),
        message.getSenderId()));

    if (!verifyClientSignature(message)) {
      logger.info(MessageFormat.format("[{0}]: Invalid Signature", this.config.getId()));
      return;
    }

    logger.info(MessageFormat.format("[{0}]: Adding transaction to pool", this.config.getId()));
    pool.addTransaction(message);

    startConsensus();

    throw new UnsupportedOperationException();
  }

  public void startConsensus() {
    Optional<Block> block = pool.getBlock();
    if (block.isPresent()) {
      logger.info(MessageFormat.format("[{0}]: Starting consensus for block", this.config.getId()));
      nodeService.waitAndStartConsensus(block.get());
    }
  }

  @Override
  public void listen() {
    try {
      // A new thread is created to listen for incoming messages
      new Thread(() -> {
        try {
          while (true) {
            Message message;

            try {
              message = link.receive();
            } catch (InvalidSignatureException e) {
              logger.info(MessageFormat.format("[{0}]: EXCEPTION: {1}", this.config.getId(),
                  e.getMessage()));
              continue;
            }

            // Each new message is handled by a new thread
            new Thread(() -> {
              switch (message.getType()) {

                case BALANCE -> balance((ClientMessage) message);

                case TRANSFER -> transfer((ClientMessage) message);

                case ACK -> {
                }

                case IGNORE -> {
                }

                default -> {
                }

              }

            }).start();
          }
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
