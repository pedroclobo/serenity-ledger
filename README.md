# HDSLedger

## Introduction

HDSLedger is a simplified permissioned (closed membership) blockchain system with high dependability
guarantees. It uses the Istanbul BFT consensus algorithm to ensure that all nodes run commands
in the same order, achieving State Machine Replication (SMR) and guarantees that all nodes
have the same state.

[Stage 1 Statement](docs/stage-1.pdf)\
[Stage 1 Report](docs/report-1.pdf)\
[Stage 2 Statement](docs/stage-2.pdf)\
[Stage 2 Report](docs/report-2.pdf)

## Requirements

- [Java 17](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html) - Programming language;

- [Maven 3.8](https://maven.apache.org/) - Build and dependency management tool;

---

## Configuration Files

### Node configuration

The configuration files for the nodes can be found inside the `resources/` folder of the `Service` module.

The following fields are required:

- `id` - Unique identifier for the node;
- `hostname` - Hostname of the node;
- `port` - Port used for communication between nodes;
- `clientPort` - Port used for communication with clients;
- `publicKeyPath` - Path to the public key of the node;
- `privateKeyPath` - Path to the private key of the node;
- `byzantineBehavior` - Behavior adopted by the node.

Below is an example of a configuration file with one node. More nodes can be added by adding more objects to the array.

```json
[{
    "id": "<NODE_ID>",
    "hostname": "localhost",
    "port": "<NODE_PORT>",
    "clientPort": "<NODE_CLIENT_PORT>",
    "publicKeyPath": "<PUBLIC_KEY_PATH>",
    "privateKeyPath": "<PRIVATE_KEY_PATH>",
    "byzantineBehavior": "<BYZANTINE_BEHAVIOR>"
}]
```

### Client configuration

The client configuration files can be found inside the `resources/` folder of the `Client` module.

The following fields are required:

- `id` - Unique identifier for the client;
- `hostname` - Hostname of the client;
- `port` - Port used for communication with nodes;
- `publicKeyPath` - Path to the public key of the client;
- `privateKeyPath` - Path to the private key of the client;
- `byzantineBehavior` - Behavior adopted by the client.

Below is an example of a configuration file with one client.

```json
[{
    "id": "<CLIENT_ID>",
    "hostname": "localhost",
    "port": "<CLIENT_PORT>",
    "publicKeyPath": "<PUBLIC_KEY_PATH>",
    "privateKeyPath": "<PRIVATE_KEY_PATH>",
    "byzantineBehavior": "<BYZANTINE_BEHAVIOR>"
}]
```

## Maven

It's possible to run the project manually by using Maven.

### Instalation

Compile and install all modules using:

```
mvn clean install -DskipTests
```

### Key Generation

To generate the public and private keys for the nodes and clients, run the following command for each node and client:

```
cd PKI
mvn compile exec:java -Dexec.args="w <priv-key-file> <pub-key-file>"
```

Then update the `clients.json` and `nodes.json` files with the path to the generated keys in the `resources/` folders of the `Client` and `Service` modules.


### Execution

Begin by running the `Service` module.

```
cd Service
mvn compile exec:java -Dexec.args="1 none/nodes.json none/clients.json 2 -v" &
mvn compile exec:java -Dexec.args="2 none/nodes.json none/clients.json 2 -v" &
mvn compile exec:java -Dexec.args="3 none/nodes.json none/clients.json 2 -v" &
mvn compile exec:java -Dexec.args="4 none/nodes.json none/clients.json 2 -v" &
```

Then, run the `Client` module.

```
cd Client
mvn compile exec:java -Dexec.args="5 none/nodes.json none/clients.json -v" &
```

### Client API

The application allows the user to interact with the blockchain through a command line interface. The following commands are available:


Transfer a given `amount` from account `source` to account `destination`. Where `source` and `destination` are the client's ids or public key file names.

```
transfer <source> <destination> <amount>
```

Obtain the balance of a certain `account`. Where `account` is the client's id or public key file name.

```
balance <account>
```

Exit the client application.

```
exit
```

### Testing

Make sure you don't have any process running on the ports defined in the configuration files. Otherwise, the tests will fail as the socket won't be able to bind to the port.

To run the tests, use the following command:

```
mvn test
```

Note that some times the tests may fail due to the socket from the previous test not being properly closed and interfering with the next test. To mitigate this, run the tests one by one with the following command:

```
mvn test -Dtest="pt.ulisboa.tecnico.hdsledger.service.<TEST_CLASS_NAME>#<TEST_METHOD_NAME>"
```
