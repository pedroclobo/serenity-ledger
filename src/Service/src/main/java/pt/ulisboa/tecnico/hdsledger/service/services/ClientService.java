package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.application.ClientRequest;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.service.models.TransactionPool;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSException;
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
  // Timer (to call consensus after a certain time)
  private Timer timer;
  private final int TIMEOUT = 3000;

  public ClientService(Link link, ProcessConfig config, ProcessConfig[] clientConfigs,
      NodeService nodeService, TransactionPool pool, boolean debug) {

    this.logger = new HDSLogger(ClientService.class.getName(), debug);
    this.link = link;
    this.config = config;
    this.clientConfigs = clientConfigs;
    this.nodeService = nodeService;
    this.pool = pool;

    this.timer = new Timer();
    this.timer.schedule(new TimerTask() {
      @Override
      public void run() {
        startConsensus();
      }
    }, 0, TIMEOUT);
  }

  public ProcessConfig getConfig() {
    return this.config;
  }

  private boolean verifyClientSignature(ClientRequest message) {

    // Find configuration of the sender
    Optional<ProcessConfig> clientConfig = Arrays.stream(this.clientConfigs)
        .filter(c -> c.getId() == message.getSenderId()).findFirst();

    if (clientConfig.isEmpty()) {
      throw new HDSException(ErrorMessage.NoSuchClient);
    }

    // Verify client signature
    try {
      PublicKey publicKey = RSACryptography.readPublicKey(clientConfig.get().getPublicKeyPath());
      if (RSACryptography.verify(message.getMessage(), publicKey, message.getSignature())) {
        return true;
      }
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      throw new HDSException(ErrorMessage.SignatureVerificationError);
    }

    logger.info(MessageFormat.format("Invalid client signature from {0}", message.getSenderId()));

    return false;
  }

  public void balance(ClientRequest message) {
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

  public void transfer(ClientRequest message) {
    logger.info(MessageFormat.format("[{0}]: Received Transfer from {1}", this.config.getId(),
        message.getSenderId()));

    if (!verifyClientSignature(message)) {
      logger.info(MessageFormat.format("[{0}]: Invalid Signature", this.config.getId()));
      return;
    }

    logger.info(MessageFormat.format("[{0}]: Adding transaction to pool", this.config.getId()));
    pool.addTransaction(message);

    startConsensus();
  }

  public void startConsensus() {
    Optional<Block> block = pool.getBlock();
    if (block.isPresent()) {
      logger.info(MessageFormat.format("[{0}]: Starting consensus for block", this.config.getId()));
      nodeService.waitAndStartConsensus(block.get());
    } else {
      logger.info(
          MessageFormat.format("[{0}]: No transactions to start consensus", this.config.getId()));
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

                case BALANCE_REQUEST -> balance((ClientRequest) message);

                case TRANSFER_REQUEST -> transfer((ClientRequest) message);

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
