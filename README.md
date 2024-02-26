# HDSLedger

## Introduction

HDSLedger is a simplified permissioned (closed membership) blockchain system with high dependability
guarantees. It uses the Istanbul BFT consensus algorithm to ensure that all nodes run commands
in the same order, achieving State Machine Replication (SMR) and guarantees that all nodes
have the same state.

## Requirements

- [Java 17](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html) - Programming language;

- [Maven 3.8](https://maven.apache.org/) - Build and dependency management tool;

---

## Configuration Files

### Node configuration

Can be found inside the `resources/` folder of the `Service` module.

```json
{
    "id": <NODE_ID>,
    "isLeader": <IS_LEADER>,
    "hostname": "localhost",
    "port": <NODE_PORT>,
    "clientPort": <NODE_CLIENT_PORT>
}
```

### Client configuration

Can be found inside the `resources/` folder of the `Client` module.

```json
{
    "id": <CLIENT_ID>,
    "hostname": "localhost",
    "port": <CLIENT_PORT>
}
```

## Maven

It's possible to run the project manually by using Maven.

### Instalation

Compile and install all modules using:

```
mvn clean install
```

### Execution

Begin by running the `Service` module.

```
cd Service
mvn compile exec:java -Dexec.args="1 regular_config.json"
```

```
cd Service
mvn compile exec:java -Dexec.args="2 regular_config.json"
```

```
cd Service
mvn compile exec:java -Dexec.args="3 regular_config.json"
```

```
cd Service
mvn compile exec:java -Dexec.args="4 regular_config.json"
```

Then, run the `Client` module.

```
cd Client
mvn compile exec:java -Dexec.args="5 regular_config.json client_config.json"
```
