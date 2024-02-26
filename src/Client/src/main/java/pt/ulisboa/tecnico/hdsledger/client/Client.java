package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.library.Library;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

import java.util.Arrays;
import java.util.Optional;
import java.util.Scanner;

public class Client {

    private static String clientsConfigPath = "src/main/resources/";
    private static String nodesConfigPath = "../Service/src/main/resources/";
    public static void main(String[] args){

        // Parse command line arguments
        final String clientId = args[0];
        nodesConfigPath += args[1];
        clientsConfigPath += args[2];
        boolean activateLogs = false;
        if (args.length == 4) {
            activateLogs = args[3].equals("-debug");
        }

        // Process client and node configs
        ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientsConfigPath);
        ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);

        // Get the client config
        Optional<ProcessConfig> clientConfig = Arrays.stream(clientConfigs)
                .filter(c -> c.getId().equals(clientId))
                .findFirst();
        if (clientConfig.isEmpty())
            throw new HDSSException(ErrorMessage.ConfigFileFormat);

        ProcessConfig config = clientConfig.get();

        // Listen for node messages
        Library library = new Library(nodeConfigs, config, activateLogs);
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
                    if (tokens.length == 2){
                        System.out.println("Appending to blockchain...");
                        System.out.println(tokens[1]);
                        library.append(tokens[1]);
                    } else {
                        System.err.println("Append takes 1 argument.");
                    }
                }
                case "exit" -> {
                    System.out.println("Exiting...");
                    scanner.close();
                    System.exit(0);
                }
                default -> {
                    System.err.println("Unrecognized command:" + line);
                }
            }
        }
    }
}
