package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.application.BalanceResponse;
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
  private static String keysPath = "../PKI/src/main/resources/keys/";

  public static void main(String[] args) {

    // Parse command line arguments
    if (args.length != 3 && args.length != 4) {
      System.err.println(
          "Usage: java Client <clientId> <nodesConfigPath> <clientsConfigPath> [--verbose|-v]");
      System.exit(1);
    }

    int clientId = Integer.parseInt(args[0]);
    nodesConfigPath += args[1];
    clientsConfigPath += args[2];
    boolean debug = false;
    if (args.length == 4) {
      // Activate logs
      debug = args[3].equals("--verbose") || args[3].equals("-v");
    }

    // Retrieve client and node configurations from files
    ProcessConfig[] clientConfigs = ProcessConfigBuilder.fromFile(clientsConfigPath);
    ProcessConfig[] nodeConfigs = ProcessConfigBuilder.fromFile(nodesConfigPath);

    // The client connects to the server using the server's specified client port
    for (ProcessConfig nodeConfig : nodeConfigs) {
      nodeConfig.setPort(nodeConfig.getClientPort());
    }

    // Retrieve the current client's config
    ProcessConfig clientConfig = Arrays.stream(clientConfigs).filter(c -> c.getId() == clientId)
        .findFirst().orElseThrow(() -> new HDSSException(ErrorMessage.ConfigFileNotFound));

    // The library is responsible for translating client's requests into
    // messages and sending them to the server
    Library library = new Library(nodeConfigs, clientConfig, debug);

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

        case "balance" -> {
          if (tokens.length == 2) {
            System.out.println("Retrieving balance...");
            BalanceResponse response = library.balance(keysPath + tokens[1]);
            System.out.printf("Balance: %d%n", response.getAmount());
          } else {
            System.err.println("Usage: balance source");
          }
        }

        case "transfer" -> {
          if (tokens.length == 4) {
            System.out.printf("Transferring %s from %s to %s...%n", tokens[3], tokens[1],
                tokens[2]);
            library.transfer(keysPath + tokens[1], keysPath + tokens[2],
                Integer.parseInt(tokens[3]));
            System.out.printf("Transfer of %s from %s to %s was successful!%n", tokens[3],
                tokens[1], tokens[2]);
          } else {
            System.err.println("Usage: transfer <source> <destination> <amount>");
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
