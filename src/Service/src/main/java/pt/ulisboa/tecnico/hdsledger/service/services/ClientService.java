package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class ClientService implements UDPService {

  private static final CustomLogger LOGGER = new CustomLogger(ClientService.class.getName());
  // Client configurations
  private final ProcessConfig[] clientConfigs;
  // Node configuration
  private final ProcessConfig config;
  // Leader configuration
  private final ProcessConfig leaderConfig;
  // Link to communicate with nodes
  private final Link link;
  // Node service
  private final NodeService nodeService;

  public ClientService(Link link, ProcessConfig config, ProcessConfig leaderConfig,
      ProcessConfig[] clientConfigs, NodeService nodeService) {

    this.link = link;
    this.config = config;
    this.leaderConfig = leaderConfig;
    this.clientConfigs = clientConfigs;
    this.nodeService = nodeService;
  }

  public ProcessConfig getConfig() {
    return this.config;
  }

  private boolean isLeader(String id) {
    return this.leaderConfig.getId().equals(id);
  }

  public void append(AppendMessage message) {
    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received Append Message from {1}",
        this.config.getId(), message.getSenderId()));
    nodeService.startConsensus(message.getValue());
  }

  @Override
    public void listen() {
        try {
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();

                        // Separate thread to handle each message
                        new Thread(() -> {
                            switch (message.getType()) {
                                case APPEND ->
                                    append((AppendMessage) message);

                                case ACK ->
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}",
                                            config.getId(), message.getSenderId()));

                                case IGNORE ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                    config.getId(), message.getSenderId()));

                                default ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received unknown message from {1}",
                                                    config.getId(), message.getSenderId()));

                            }

                        }).start();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
