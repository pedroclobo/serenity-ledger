package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.service.services.ClientService;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
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

    try {
      // Command line arguments
      String id = args[0];
      nodesConfigPath += args[1];

      // Create configuration instances
      ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);
      ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientsConfigPath);
      ProcessConfig leaderConfig =
          Arrays.stream(nodeConfigs).filter(ProcessConfig::isLeader).findAny().get();
      ProcessConfig nodeConfig =
          Arrays.stream(nodeConfigs).filter(c -> c.getId().equals(id)).findAny().get();

      LOGGER.log(Level.INFO,
          MessageFormat.format(
              "Node {0}:\nNode Socket: {1}:{2}\nClient Socket: {3}:{4}\nIs Leader? {5}",
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

      nodeService.listen();
      clientService.listen();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
