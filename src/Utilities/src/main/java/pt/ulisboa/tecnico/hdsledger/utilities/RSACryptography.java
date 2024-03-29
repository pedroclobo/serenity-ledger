package pt.ulisboa.tecnico.hdsledger.utilities;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RSACryptography {

  public static String serializePublicKey(PublicKey key) {
    return Base64.getEncoder().encodeToString(key.getEncoded());
  }

  public static PublicKey deserializePublicKey(String key) {
    byte[] keyBytes = Base64.getDecoder().decode(key);
    try {
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  private static byte[] readFromFile(String path) {
    try {
      FileInputStream fis = new FileInputStream(path);
      byte[] data = new byte[fis.available()];
      fis.read(data);
      fis.close();
      return data;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static PublicKey readPublicKey(String path)
      throws InvalidKeySpecException, NoSuchAlgorithmException {
    byte[] keyBytes = readFromFile(path);
    X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
    KeyFactory factory = KeyFactory.getInstance("RSA");
    return factory.generatePublic(spec);
  }

  public static PrivateKey readPrivateKey(String path)
      throws InvalidKeySpecException, NoSuchAlgorithmException {
    byte[] keyBytes = readFromFile(path);
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
    KeyFactory factory = KeyFactory.getInstance("RSA");
    return factory.generatePrivate(spec);
  }

  public static String sign(String data, PrivateKey privateKey)
      throws InvalidKeyException, NoSuchAlgorithmException, SignatureException {
    java.security.Signature rsa = java.security.Signature.getInstance("SHA256withRSA");
    rsa.initSign(privateKey);
    rsa.update(data.getBytes());
    byte[] signature = rsa.sign();

    return Base64.getEncoder().encodeToString(signature);
  }

  public static boolean verify(String data, PublicKey publicKey, String signature)
      throws InvalidKeySpecException, NoSuchAlgorithmException {
    java.security.Signature rsa = java.security.Signature.getInstance("SHA256withRSA");
    try {
      rsa.initVerify(publicKey);
      rsa.update(data.getBytes());
      return rsa.verify(Base64.getDecoder().decode(signature));
    } catch (InvalidKeyException | SignatureException e) {
      e.printStackTrace();
      return false;
    }
  }

  public static String digest(String data) throws NoSuchAlgorithmException {
    byte[] dataBytes = data.getBytes();
    final String DIGEST_ALGO = "SHA-256";
    MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGO);
    messageDigest.update(dataBytes);
    byte[] digestBytes = messageDigest.digest();

    return Base64.getEncoder().encodeToString(digestBytes);
  }
}
