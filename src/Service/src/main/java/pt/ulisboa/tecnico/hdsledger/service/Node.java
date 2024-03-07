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
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig.ByzantineBehavior;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;

public class Node {

  private static String nodesConfigPath = "src/main/resources/";
  private static String clientsConfigPath = "../Client/src/main/resources/";

  private final CustomLogger logger;

  private Link linkToNodes;
  private Link linkToClients;
  private NodeService nodeService;
  private ClientService clientService;

  public static void main(String[] args) {

    // Parse command line arguments
    if (args.length != 4) {
      System.err
          .println("Usage: java Node <nodeId> <nodesConfigPath> <clientsConfigPath> [true|false]");
      System.exit(1);
    }
    int id = Integer.parseInt(args[0]);
    nodesConfigPath += args[1];
    clientsConfigPath += args[2];
    boolean activateLogs = Boolean.parseBoolean(args[3]);

    // Retrieve nodes and client configurations
    ProcessConfig[] nodeConfigs = ProcessConfigBuilder.fromFile(nodesConfigPath);
    ProcessConfig[] clientConfigs = ProcessConfigBuilder.fromFile(clientsConfigPath);

    Node node = new Node(id, nodeConfigs, clientConfigs, activateLogs);
    node.start();

    // Wait for the user to terminate the program
    System.out.println("Press enter to terminate the program.");
    System.console().readLine();
    node.shutdown();
  }

  public Node(int id, ProcessConfig[] nodeConfigs, ProcessConfig[] clientConfigs,
      boolean activateLogs) {
    logger = new CustomLogger(Node.class.getName(), activateLogs);

    // Retrieve the current node's config and the leader's config
    ProcessConfig nodeConfig = Arrays.stream(nodeConfigs).filter(c -> c.getId() == id).findAny()
        .orElseThrow(() -> new HDSSException(ErrorMessage.NoSuchNode));

    logger.log(MessageFormat.format(
        "Node with id {0} with node socket on <{1}:{2}> and client socket on <{3}:{4}> and leader={5}",
        nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort(),
        nodeConfig.getHostname(), nodeConfig.getClientPort(), nodeConfig.isLeader(1, 1)));

    // Abstraction to send and receive messages
    linkToNodes = new Link(nodeConfig, nodeConfig.getPort(), nodeConfigs, ConsensusMessage.class);
    linkToClients =
        new Link(nodeConfig, nodeConfig.getClientPort(), clientConfigs, AppendMessage.class);

    // Services that implement listen from UDPService
    nodeService =
        new NodeService(linkToNodes, linkToClients, nodeConfig, nodeConfigs, activateLogs);
    clientService =
        new ClientService(linkToClients, nodeConfig, nodeConfigs, nodeService, activateLogs);
  }

  public void start() {
    try {
      nodeService.listen();
      clientService.listen();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void shutdown() {
    nodeService.shutdown();
    clientService.shutdown();
  }

  public NodeService getNodeService() {
    return nodeService;
  }

  public ByzantineBehavior getByzantineBehavior() {
    return nodeService.getConfig().getByzantineBehavior();
  }

}
