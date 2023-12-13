package io.confluent.developer;


import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;


/**
 * Utility class for loading Properties from a file.
 */
public class PropertiesUtil {

  public static Properties loadProperties(String fileName) throws IOException {
    try (FileInputStream input = new FileInputStream(fileName)) {
      final Properties props = new Properties();
      props.load(input);
      return props;
    }
  }

}
