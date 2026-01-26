package dev.nilswitt.rk.edpmonitoring.connectors;

import dev.nilswitt.rk.edpmonitoring.Utilities;
import dev.nilswitt.rk.edpmonitoring.enitites.Unit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.UUID;

public class ConfigConnector {
    private static final Logger LOGGER = LogManager.getLogger(ConfigConnector.class);
    private static ConfigConnector instance = new ConfigConnector();
    private Properties props = new Properties();
    private HashMap<String, UUID> unitMapping = new HashMap<>();
    private HashMap<UUID, Unit> units = new HashMap<>();

    private MiniOConnector miniOConnector;
    private MariaDBConnector mariaDBConnector;

    public MariaDBConnector getMariaDBConnector() {
        return mariaDBConnector;
    }

    public void setMariaDBConnector(MariaDBConnector mariaDBConnector) {
        this.mariaDBConnector = mariaDBConnector;
    }

    private ApiConnector apiConnector;

    public ApiConnector getApiConnector() {
        return apiConnector;
    }

    public void setApiConnector(ApiConnector apiConnector) {
        this.apiConnector = apiConnector;
    }

    private ConfigConnector() {
        loadConfig();
    }

    public static ConfigConnector getInstance() {
        return instance;
    }

    private Properties loadConfig() {
        Properties props = new Properties();

        Path cfgFile = Paths.get(Utilities.getCurrentWorkingDirectory().toString(), "config.properties");
        if (Files.exists(cfgFile)) {
            try (InputStream in = new FileInputStream(cfgFile.toFile())) {
                props.load(in);
                LOGGER.info("Loaded configuration from {}", cfgFile.toAbsolutePath());
            } catch (IOException e) {
                LOGGER.warn("Failed to read config.properties: {}. Falling back to environment variables.", e.getMessage());
            }
        } else {
            LOGGER.info("No config.properties found in working directory; using environment variables or defaults. " + cfgFile.toAbsolutePath().toString());
        }
        this.props = props;

        HashSet<String> keys = new HashSet<>();
        props.keySet().forEach(key -> {
            String k = (String) key;
            if (k.startsWith("units.")) {
                String id = getUnitId(k);
                if (!keys.contains(id)) {
                    keys.add(id);
                }
            }
        });

        for (String key : keys) {
            String unitApiId = props.getProperty("units." + key + ".api_id");
            String unitName = props.getProperty("units." + key + ".name");
            if (unitApiId != null && unitName != null) {
                this.unitMapping.put(unitName, UUID.fromString(unitApiId));
                LOGGER.info("Loaded unit mapping: {} -> {}", unitName, unitApiId);
            } else {
                LOGGER.warn("Incomplete unit mapping for key {}: api_id or name missing", key);
            }
        }
        return props;
    }

    private String getUnitId(String key) {
        String rmPrefix = key.substring("units.".length());
        return rmPrefix.substring(0, rmPrefix.indexOf('.'));
    }

    public String getConfigValue(String key, String envKey, String defaultVal) {
        String v = this.props.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        String ev = System.getenv(envKey);
        if (ev != null && !ev.isEmpty()) return ev;
        return defaultVal;
    }

    public HashMap<String, UUID> getUnitMappings() {
        return this.unitMapping;
    }

    public HashMap<UUID, Unit> getUnits() {
        return this.units;
    }

    public MiniOConnector getMiniOConnector() {
        return miniOConnector;
    }

    public void setMiniOConnector(MiniOConnector miniOConnector) {
        this.miniOConnector = miniOConnector;
    }
}
