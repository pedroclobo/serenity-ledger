package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class AppendMessage extends Message {

  private String value;

  public AppendMessage(String senderId, Type type, String value) {
    super(senderId, type);
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public AppendMessage deserialize() {
    return new Gson().fromJson(value, AppendMessage.class);
  }

  @Override
  public String toString() {
    return "AppendMessage [value=" + value + "]";
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!(obj instanceof AppendMessage))
      return false;

    AppendMessage other = (AppendMessage) obj;

    return value.equals(other.value);
  }

}
