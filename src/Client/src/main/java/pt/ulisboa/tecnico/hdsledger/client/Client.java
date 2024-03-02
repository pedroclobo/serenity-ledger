package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.library.Library;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import java.util.Arrays;
import java.util.Scanner;

public class Client {

  private static String clientsConfigPath = "src/main/resources/";
  private static String nodesConfigPath = "../Service/src/main/resources/";

  public static void main(String[] args) {

    // Parse command line arguments
    if (args.length != 3 && args.length != 4) {
      System.err.println(
          "Usage: java Client <clientId> <nodesConfigPath> <clientsConfigPath> [--verbose|-v]");
      System.exit(1);
    }

    String clientId = args[0];
    nodesConfigPath += args[1];
    clientsConfigPath += args[2];
    boolean activateLogs = false;
    if (args.length == 4) {
      // Activate logs
      activateLogs = args[3].equals("--verbose") || args[3].equals("-v");
    }

    // Retrieve client and node configurations from files
    ProcessConfig[] clientConfigs = ProcessConfigBuilder.fromFile(clientsConfigPath);
    ProcessConfig[] nodeConfigs = ProcessConfigBuilder.fromFile(nodesConfigPath);

    // The client connects to the server using the server's specified client port
    for (ProcessConfig nodeConfig : nodeConfigs) {
      nodeConfig.setPort(nodeConfig.getClientPort());
    }

    // Retrieve the current client's config
    ProcessConfig clientConfig =
        Arrays.stream(clientConfigs).filter(c -> c.getId().equals(clientId)).findFirst()
            .orElseThrow(() -> new HDSSException(ErrorMessage.ConfigFileNotFound));

    // The library is responsible for translating client's requests into
    // messages and sending them to the server
    Library library = new Library(nodeConfigs, clientConfig, activateLogs);
    library.listen();

    final Scanner scanner = new Scanner(System.in);

    while (true) {
      System.out.printf("%n> ");
      String line = scanner.nextLine();

      // Empty command
      if (line.trim().length() == 0) {
        System.out.println();
        continue;
      }

      String[] tokens = line.split(" ");

      switch (tokens[0]) {
        case "append" -> {
          if (tokens.length == 2) {
            System.out.printf("Appending %s to blockchain...%n", tokens[1]);
            library.append(tokens[1]);
            System.out.printf("Value %s was appended with success!%n", tokens[1]);
          } else {
            System.err.println("Usage: append <str>");
          }
        }
        case "exit" -> {
          System.out.println("Exiting...");
          scanner.close();
          library.shutdown();
          System.exit(0);
        }
        default -> {
          System.err.println("Unrecognized command: " + line);
        }
      }
    }
  }
}
