package dev.nilswitt.rk.edpmonitoring.connectors;

import dev.nilswitt.rk.edpmonitoring.Main;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfigConnector {
    private static final Logger logger = LogManager.getLogger(ConfigConnector.class);
    private static ConfigConnector instance = new ConfigConnector();
    private Properties props = new Properties();

    private ConfigConnector() {
        loadConfig();
    }

    public static ConfigConnector getInstance() {
        return instance;
    }

    private Properties loadConfig() {
        Properties props = new Properties();
        Path cfgFile = Paths.get("config.properties");
        if (Files.exists(cfgFile)) {
            try (InputStream in = new FileInputStream(cfgFile.toFile())) {
                props.load(in);
                logger.info("Loaded configuration from {}", cfgFile.toAbsolutePath());
            } catch (IOException e) {
                logger.warn("Failed to read config.properties: {}. Falling back to environment variables.", e.getMessage());
            }
        } else {
            logger.info("No config.properties found in working directory; using environment variables or defaults. " + cfgFile.toAbsolutePath().toString());
        }
        this.props = props;
        return props;
    }

    public String getConfigValue(String key, String envKey, String defaultVal) {
        String v = this.props.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        String ev = System.getenv(envKey);
        if (ev != null && !ev.isEmpty()) return ev;
        return defaultVal;
    }

}
