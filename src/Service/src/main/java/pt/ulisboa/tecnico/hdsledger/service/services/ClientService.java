package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.net.SocketException;
import java.text.MessageFormat;
import java.util.logging.Level;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.exceptions.InvalidSignatureException;

public class ClientService implements UDPService {

  private final CustomLogger logger;
  // Client configurations
  private final ProcessConfig[] clientConfigs;
  // Node configuration
  private final ProcessConfig config;
  // Link to communicate with nodes
  private final Link link;
  // Node service
  private final NodeService nodeService;

  public ClientService(Link link, ProcessConfig config, ProcessConfig[] clientConfigs,
      NodeService nodeService, boolean activateLogs) {

    this.logger = new CustomLogger(ClientService.class.getName(), activateLogs);
    this.link = link;
    this.config = config;
    this.clientConfigs = clientConfigs;
    this.nodeService = nodeService;
  }

  public ProcessConfig getConfig() {
    return this.config;
  }

  public void append(AppendMessage message) {
    logger.log(MessageFormat.format("[{0}]: Received Append from {1} with value {2}",
        config.getId(), message.getSenderId(), message.getValue()));
    nodeService.startConsensus(message.getValue());
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
              logger.log(MessageFormat.format("{0} - EXCEPTION: {1}", this.config.getId(),
                  e.getMessage()));
              continue;
            }

            // Each new message is handled by a new thread
            new Thread(() -> {
              switch (message.getType()) {
                case APPEND -> append((AppendMessage) message);

                case ACK -> logger.log(MessageFormat.format("[{0}]: Received ACK from {1}",
                    config.getId(), message.getSenderId()));

                case IGNORE -> {
                }

                default ->
                  logger.log(MessageFormat.format("[{0}]: Received unknown message from {1}",
                      config.getId(), message.getSenderId()));

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
