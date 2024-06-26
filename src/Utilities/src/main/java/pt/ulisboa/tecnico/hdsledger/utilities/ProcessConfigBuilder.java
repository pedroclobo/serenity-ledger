package pt.ulisboa.tecnico.hdsledger.utilities;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ProcessConfigBuilder {

  public static ProcessConfig[] fromFile(String path) {
    try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(path))) {
      String input = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      return fromJson(input);
    } catch (FileNotFoundException e) {
      throw new HDSException(ErrorMessage.ConfigFileNotFound);
    } catch (IOException | JsonSyntaxException e) {
      throw new HDSException(ErrorMessage.ConfigFileFormat);
    }
  }

  public static ProcessConfig[] fromJson(String json) {
    Gson gson = new Gson();
    ProcessConfig[] configs = gson.fromJson(json, ProcessConfig[].class);
    int n = configs.length;
    for (ProcessConfig config : configs) {
      config.setN(n);
    }
    return configs;
  }

}
