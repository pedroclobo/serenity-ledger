package pt.ulisboa.tecnico.hdsledger.library;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;

public class ClientMessageBucket {

  private final int f_1;

  // Map from value to (ack append messages, senderId)
  private final Map<String, Map<Integer, AppendMessage>> appendBucket = new ConcurrentHashMap<>();

  public ClientMessageBucket(int nodeCount) {
    f_1 = Math.floorDiv(nodeCount - 1, 3) + 1;
  }

  // Returns true if there are f+1 messages from different senders
  public boolean addAppendMessage(AppendMessage message) {
    int senderId = message.getSenderId();
    String value = message.getValue();

    appendBucket.putIfAbsent(value, new ConcurrentHashMap<>());
    appendBucket.get(value).putIfAbsent(senderId, message);

    return appendBucket.get(value).size() >= f_1;
  }

}
