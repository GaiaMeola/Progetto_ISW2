package util;

import analyzer.exception.ConfigurationLoadException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigurationLoader {

    private ConfigurationLoader(){
        // Prevent instantation
    }
    private static final Properties props = new Properties();

    static {
        try (InputStream input = ConfigurationLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
            } else {
                throw new ConfigurationLoadException("File config.properties non trovato");
            }
        } catch (IOException e) {
            throw new ConfigurationLoadException("Errore caricando config.properties", e);
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }
}
