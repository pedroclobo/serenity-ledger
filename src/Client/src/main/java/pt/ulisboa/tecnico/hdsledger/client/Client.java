package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.application.BalanceResponse;
import pt.ulisboa.tecnico.hdsledger.communication.application.TransferResponse;
import pt.ulisboa.tecnico.hdsledger.library.Library;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

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

    // The library is responsible for translating client's requests into
    // messages and sending them to the server
    Library library = new Library(clientId, nodeConfigs, clientConfigs, debug);

    final Scanner scanner = new Scanner(System.in);

    while (true) {
      System.out.printf("%n> ");
      String line = scanner.nextLine();

      // Empty command
      if (line.trim().isEmpty()) {
        System.out.println();
        continue;
      }

      String[] tokens = line.split(" ");

      switch (tokens[0]) {

        case "balance" -> {
          if (tokens.length == 2) {
            BalanceResponse response;
            System.out.println("Retrieving balance...");
            try {
              // Balance can be retrieved by using the client's id or the client's public key
              if (isNotInteger(tokens[1])) {
                response = library.balance(keysPath + tokens[1]);
              } else {
                response = library.balance(Integer.parseInt(tokens[1]));
              }
              // Check if the balance was successfully retrieved
              if (!response.successful()) {
                System.err.println("Operation failed");
              } else {
                System.out.printf("Balance: %d%n", response.amount().get());
              }
            } catch (Exception e) {
              System.err.println("Operation failed with message: " + e.getMessage());
            }
          } else {
            System.err.println("Usage: balance <source>");
          }
        }

        case "transfer" -> {
          if (tokens.length == 4) {
            TransferResponse response;
            System.out.printf("Transferring %s from %s to %s...%n", tokens[3], tokens[1],
                tokens[2]);
            try {
              // Transfer can be done by using the client's id or the client's public key
              if (isNotInteger(tokens[1]) || isNotInteger(tokens[2])) {
                response = library.transfer(keysPath + tokens[1], keysPath + tokens[2],
                    Integer.parseInt(tokens[3]));
              } else {
                response = library.transfer(Integer.parseInt(tokens[1]),
                    Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]));
              }

              // Check if the transfer was successful
              if (!response.successful()) {
                System.err.println("Operation failed");
              } else {
                System.out.printf("Transfer of %s from %s to %s was successful!%n", tokens[3],
                    tokens[1], tokens[2]);
              }
            } catch (Exception e) {
              System.err.println("Operation failed with message: " + e.getMessage());
            }
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

  private static boolean isNotInteger(String s) {
    try {
      Integer.parseInt(s);
      return false;
    } catch (NumberFormatException e) {
      return true;
    }
  }
}
