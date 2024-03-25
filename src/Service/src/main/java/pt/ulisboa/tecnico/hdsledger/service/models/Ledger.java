package pt.ulisboa.tecnico.hdsledger.service.models;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.RSACryptography;

public class Ledger {

  private List<Block> ledger;
  // Map from public key hash to account
  private Map<String, Account> accounts;

  public Ledger(ProcessConfig[] nodeConfigs, ProcessConfig[] clientConfigs) {
    ledger = new ArrayList<>();

    accounts = new HashMap<>();

    // Create node accounts
    for (ProcessConfig config : nodeConfigs) {
      String publicKeyPath = config.getPublicKeyPath();
      PublicKey publicKey;
      String publicKeyHash;
      try {
        publicKey = RSACryptography.readPublicKey(publicKeyPath);
        publicKeyHash = RSACryptography.digest(publicKey.toString());
      } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
        throw new RuntimeException("Error reading public key");
      }

      accounts.put(publicKeyHash, new Account(config.getId(), config.getPublicKeyPath()));
    }

    // Create client accounts
    for (ProcessConfig config : clientConfigs) {
      String publicKeyPath = config.getPublicKeyPath();
      PublicKey publicKey;
      String publicKeyHash;
      try {
        publicKey = RSACryptography.readPublicKey(publicKeyPath);
        publicKeyHash = RSACryptography.digest(publicKey.toString());
      } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
        throw new RuntimeException("Error reading public key");
      }

      accounts.put(publicKeyHash, new Account(config.getId(), config.getPublicKeyPath()));
    }
  }

  public List<Block> getLedger() {
    return ledger;
  }

  public Account getAccount(String publicKeyHash) {
    return accounts.get(publicKeyHash);
  }

  public void add(Block block) {
    synchronized (ledger) {
      ledger.add(block);
    }
  }
}
