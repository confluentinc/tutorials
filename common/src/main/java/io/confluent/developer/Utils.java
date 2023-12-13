package io.confluent.developer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

public class Utils {

    private Utils() {}
    public static final String DEFAULT_PROPERTIES_LOCATION = "src/main/resources/confluent.properties";

    public static Properties loadProperties(final String path) {
        Properties properties = new Properties();
        try (InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(path))) {
            properties.load(inputStreamReader);
            return properties;
        } catch (IOException e) {
            throw new RuntimeException("Problem loading properties file for example", e);
        }
    }

    public static Properties loadProperties() {
      return loadProperties(DEFAULT_PROPERTIES_LOCATION);
    }
}
