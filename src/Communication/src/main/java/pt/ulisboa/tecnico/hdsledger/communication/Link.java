package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.communication.consensus.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.utilities.exceptions.InvalidSignatureException;

import java.io.IOException;
import java.net.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Link {

  private final HDSLogger logger;
  // Time to wait for an ACK before resending the message
  private final int BASE_SLEEP_TIME;
  // UDP Socket
  private final DatagramSocket socket;
  // Map of all nodes in the network
  private final Map<Integer, ProcessConfig> nodes = new ConcurrentHashMap<>();
  // Reference to the node itself
  private final ProcessConfig config;
  // Class to deserialize messages to
  private final Class<? extends Message> messageClass;
  // Set of received messages from specific node (prevent duplicates)
  private final Map<Integer, CollapsingSet> receivedMessages = new ConcurrentHashMap<>();
  // Set of received ACKs from specific node
  private final CollapsingSet receivedAcks = new CollapsingSet();
  // Message counter
  private final AtomicInteger messageCounter = new AtomicInteger(0);
  // Send messages to self by pushing to queue instead of through the network
  private final Queue<Message> localhostQueue = new ConcurrentLinkedQueue<>();

  public Link(ProcessConfig self, int port, ProcessConfig[] nodes,
      Class<? extends Message> messageClass) {
    this(self, port, nodes, messageClass, false, 200);
  }

  public Link(ProcessConfig self, int port, ProcessConfig[] nodes,
      Class<? extends Message> messageClass, boolean debug, int baseSleepTime) {

    this.logger = new HDSLogger(Link.class.getName(), debug);

    this.config = self;
    this.messageClass = messageClass;
    this.BASE_SLEEP_TIME = baseSleepTime;

    Arrays.stream(nodes).forEach(node -> {
      int id = node.getId();
      this.nodes.put(id, node);
      receivedMessages.put(id, new CollapsingSet());
    });

    try {
      this.socket = new DatagramSocket(port, InetAddress.getByName(config.getHostname()));
    } catch (UnknownHostException | SocketException e) {
      throw new HDSException(ErrorMessage.CannotOpenSocket);
    }
  }

  public void ackAll(List<Integer> messageIds) {
    receivedAcks.addAll(messageIds);
  }

  /*
   * Broadcasts a message to all nodes in the network
   *
   * @param data The message to be broadcasted
   */
  public void broadcast(Message data) {
    Gson gson = new Gson();
    nodes
        .forEach((destId, dest) -> send(destId, gson.fromJson(gson.toJson(data), data.getClass())));
  }

  public void quorumMulticast(Message data) {
    int f = (nodes.size() - 1) / 3;
    Collection<Integer> destIds =
        nodes.keySet().stream().limit(2 * f + 1).collect(Collectors.toList());

    Gson gson = new Gson();
    for (int destId : destIds) {
      send(destId, gson.fromJson(gson.toJson(data), data.getClass()));
    }
  }

  public void smallMulticast(Message data) {
    int f = (nodes.size() - 1) / 3;
    Collection<Integer> destIds = nodes.keySet().stream().limit(f + 1).collect(Collectors.toList());

    Gson gson = new Gson();
    for (int destId : destIds) {
      send(destId, gson.fromJson(gson.toJson(data), data.getClass()));
    }
  }

  /*
   * Sends a message to a specific node with guarantee of delivery
   *
   * @param nodeId The node identifier
   *
   * @param data The message to be sent
   */
  public void send(int nodeId, Message data) {

    // Spawn a new thread to send the message
    // To avoid blocking while waiting for ACK
    new Thread(() -> {
      try {
        ProcessConfig node = nodes.get(nodeId);
        if (node == null)
          throw new HDSException(ErrorMessage.NoSuchNode);

        data.setMessageId(messageCounter.getAndIncrement());

        // If the message is not ACK, it will be resent
        InetAddress destAddress = InetAddress.getByName(node.getHostname());
        int destPort = node.getPort();
        int count = 1;
        int messageId = data.getMessageId();
        int sleepTime = BASE_SLEEP_TIME;

        // Send message to local queue instead of using network if destination in self
        if (nodeId == this.config.getId()) {
          this.localhostQueue.add(data);

          logger
              .info(MessageFormat.format("{0} - Message {1} (locally) sent to {2}:{3} successfully",
                  config.getId(), data.getType(), destAddress, destPort));

          return;
        }

        for (;;) {
          logger.info(MessageFormat.format(
              "{0} - Sending {1} message to {2}:{3} with message ID {4} - Attempt #{5}",
              config.getId(), data.getType(), destAddress, destPort, messageId, count++));

          unreliableSend(destAddress, destPort, data);

          // Wait (using exponential back-off), then look for ACK
          Thread.sleep(sleepTime);

          // Receive method will set receivedAcks when sees corresponding ACK
          if (receivedAcks.contains(messageId))
            break;

          sleepTime <<= 1;
        }

        logger.info(MessageFormat.format("{0} - Message {1} sent to {2}:{3} successfully",
            config.getId(), data.getType(), destAddress, destPort));
      } catch (InterruptedException | UnknownHostException e) {
        e.printStackTrace();
      }
    }).start();
  }

  /*
   * Sends a message to a specific node without guarantee of delivery Mainly used to send ACKs, if
   * they are lost, the original message will be resent
   *
   * @param address The address of the destination node
   *
   * @param port The port of the destination node
   *
   * @param data The message to be sent
   */
  public void unreliableSend(InetAddress hostname, int port, Message data) {
    new Thread(() -> {
      try {
        SignedMessage signedMessage = new SignedMessage(data);
        try {
          signedMessage.sign(config.getPrivateKeyPath());
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException
            | InvalidKeySpecException e) {
          e.printStackTrace();
          throw new HDSException(ErrorMessage.SigningError);
        }

        byte[] buf = new Gson().toJson(signedMessage).getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, hostname, port);
        socket.send(packet);
      } catch (SocketException e) {
        // Supress message during shutdown
      } catch (IOException e) {
        e.printStackTrace();
        throw new HDSException(ErrorMessage.SocketSendingError);
      }
    }).start();
  }

  /*
   * Receives a message from any node in the network (blocking)
   */
  public Message receive() throws IOException, ClassNotFoundException, InvalidSignatureException {

    Message message = null;
    SignedMessage signedMessage = null;
    String serialized = "";
    Boolean local = false;
    DatagramPacket response = null;

    if (this.localhostQueue.size() > 0) {
      message = this.localhostQueue.poll();
      local = true;
      this.receivedAcks.add(message.getMessageId());
    } else {
      byte[] buf = new byte[65535];
      response = new DatagramPacket(buf, buf.length);

      socket.receive(response);

      byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
      serialized = new String(buffer);
      signedMessage = new Gson().fromJson(serialized, SignedMessage.class);
      message = new Gson().fromJson(signedMessage.getMessageJson(), Message.class);

      try {
        if (!signedMessage.verify(nodes.get(message.getSenderId()).getPublicKeyPath())) {
          logger
              .info(MessageFormat.format("{0} - Message from {1} with ID {2} has invalid signature",
                  config.getId(), message.getSenderId(), message.getMessageId()));

          throw new InvalidSignatureException(ErrorMessage.InvalidSignature.getMessage());
        }
      } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException
          | InvalidKeySpecException e) {
        e.printStackTrace();
        throw new HDSException(ErrorMessage.SignatureVerificationError);
      }
    }

    int senderId = message.getSenderId();
    int messageId = message.getMessageId();

    if (!nodes.containsKey(senderId))
      throw new HDSException(ErrorMessage.NoSuchNode);

    // Handle ACKS, since it's possible to receive multiple acks from the same
    // message
    if (message.getType().equals(Message.Type.ACK)) {
      receivedAcks.add(messageId);
      return message;
    }

    // It's not an ACK -> Deserialize for the correct type
    if (!local)
      message = new Gson().fromJson(signedMessage.getMessageJson(), this.messageClass);

    boolean isRepeated = !receivedMessages.get(message.getSenderId()).add(messageId);
    Type originalType = message.getType();
    // Message already received (add returns false if already exists) => Discard
    if (isRepeated) {
      message.setType(Message.Type.IGNORE);
    }

    switch (message.getType()) {
      case PRE_PREPARE -> {
        return message;
      }
      case IGNORE -> {
        if (!originalType.equals(Type.COMMIT))
          return message;
      }
      case PREPARE -> {
        ConsensusMessage consensusMessage = (ConsensusMessage) message;
        if (consensusMessage.getReplyTo() == config.getId())
          receivedAcks.add(consensusMessage.getReplyToMessageId());

        return message;
      }
      case COMMIT -> {
        ConsensusMessage consensusMessage = (ConsensusMessage) message;
        if (consensusMessage.getReplyTo() == config.getId())
          receivedAcks.add(consensusMessage.getReplyToMessageId());
      }
      default -> {
      }
    }

    if (!local) {
      InetAddress address = InetAddress.getByName(response.getAddress().getHostAddress());
      int port = response.getPort();

      Message responseMessage = new Message(this.config.getId(), Message.Type.ACK);
      responseMessage.setMessageId(messageId);

      // ACK is sent without needing for another ACK because
      // we're assuming an eventually synchronous network
      // Even if a node receives the message multiple times,
      // it will discard duplicates
      unreliableSend(address, port, responseMessage);
    }

    return message;
  }

  public void shutdown() {
    socket.close();
  }
}
