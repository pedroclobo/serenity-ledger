package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.service.services.ClientService;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;

public class Node {

  private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());

  private static String nodesConfigPath = "src/main/resources/";
  private static String clientsConfigPath = "../Client/src/main/resources/client_config.json";

  public static void main(String[] args) {

    // Parse command line arguments
    if (args.length != 2) {
      System.err.println("Usage: java Node <nodeId> <nodesConfigPath>");
      System.exit(1);
    }
    String id = args[0];
    nodesConfigPath += args[1];

    // Retrieve nodes and client configurations
    ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);
    ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientsConfigPath);

    LOGGER.log(Level.INFO, MessageFormat.format(
        "Node configuration: {0}\nClient Configuration: {1}", nodesConfigPath, clientsConfigPath));

    // Retrieve the current node's config and the leader's config
    ProcessConfig nodeConfig = Arrays.stream(nodeConfigs).filter(c -> c.getId().equals(id))
        .findAny().orElseThrow(() -> new HDSSException(ErrorMessage.NoSuchNode));

    ProcessConfig leaderConfig = Arrays.stream(nodeConfigs).filter(ProcessConfig::isLeader)
        .findAny().orElseThrow(() -> new HDSSException(ErrorMessage.NoLeaderNode));

    LOGGER.log(Level.INFO,
        MessageFormat.format(
            "Node with id {0} with node socket on <{1}:{2}> and client socket on <{3}:{4}> and leader={5}",
            nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort(),
            nodeConfig.getHostname(), nodeConfig.getClientPort(), nodeConfig.isLeader()));

    // Abstraction to send and receive messages
    Link linkToNodes =
        new Link(nodeConfig, nodeConfig.getPort(), nodeConfigs, ConsensusMessage.class);
    Link linkToClients =
        new Link(nodeConfig, nodeConfig.getClientPort(), clientConfigs, AppendMessage.class);

    // Services that implement listen from UDPService
    NodeService nodeService = new NodeService(linkToNodes, nodeConfig, leaderConfig, nodeConfigs);
    ClientService clientService =
        new ClientService(linkToClients, nodeConfig, leaderConfig, nodeConfigs, nodeService);

    try {
      nodeService.listen();
      clientService.listen();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
