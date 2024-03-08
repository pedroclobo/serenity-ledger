package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.ulisboa.tecnico.hdsledger.utilities.OptionalTypeAdapter;
import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

public class CommitQuorumMessage {

  private Set<CommitMessage> quorum;

  public CommitQuorumMessage(Set<CommitMessage> quorum) {
    this.quorum = quorum;
  }

  public Set<CommitMessage> getQuorum() {
    return quorum;
  }

  public boolean verifyValueSignature(String publicKeyPath, String value)
      throws InvalidKeySpecException, NoSuchAlgorithmException {
    return RSACryptography.verify(publicKeyPath, value,
        this.quorum.iterator().next().getValueSignature());
  }

  public String toJson() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapterFactory(OptionalTypeAdapter.FACTORY);
    Gson gson = gsonBuilder.create();

    return gson.toJson(this);
  }
}
