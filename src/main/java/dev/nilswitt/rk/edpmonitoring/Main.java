package dev.nilswitt.rk.edpmonitoring;

import dev.nilswitt.rk.edpmonitoring.connectors.ApiConnector;
import dev.nilswitt.rk.edpmonitoring.connectors.ConfigConnector;
import dev.nilswitt.rk.edpmonitoring.connectors.MariaDBConnector;
import dev.nilswitt.rk.edpmonitoring.enitites.Unit;
import dev.nilswitt.rk.edpmonitoring.threads.OutBoxWatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {

        logger.info("Working Directory: {}", Utilities.getCurrentWorkingDirectory());


        ConfigConnector configConnector = ConfigConnector.getInstance();

        String apiUrl = configConnector.getConfigValue("api.url", "API_URL", "http://localhost:8080/api");
        logger.info("Using API URL: {}", apiUrl);
        String apiToken = configConnector.getConfigValue("api.token", "API_TOKEN", "default_token");
        logger.info("Using API Token: {}", apiToken.substring(0, 5));

        ApiConnector apiConnector = new ApiConnector(apiUrl, apiToken, configConnector);
        if (apiConnector.testConnection()) {
            logger.info("API connection test successful.");
        } else {
            logger.error("API connection test failed. Exiting.");
            System.exit(1);
        }

        String url = configConnector.getConfigValue("db.url", "DB_URL", "jdbc:mariadb://localhost:3306/edp_monitoring");
        String user = configConnector.getConfigValue("db.user", "DB_USER", "edp_user");
        String pass = configConnector.getConfigValue("db.password", "DB_PASSWORD", "edp_password");

        logger.info("Using DB URL: {} (user={})", url, user);
        MariaDBConnector mariaDBConnector = new MariaDBConnector(url, user, pass);
        if (mariaDBConnector.testConnection()) {
            logger.info("Database connection test successful.");
        } else {
            logger.error("Database connection test failed. Exiting.");
            System.exit(1);
        }

        try {
            Path logs = Paths.get(System.getProperty("user.home"), "logs");
            if (!Files.exists(logs)) {
                Files.createDirectories(logs);
                logger.info("Created logs directory: {}", logs.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to create logs directory: " + e.getMessage());
            e.printStackTrace(System.err);
            // continue; logging may still work if directory exists or if configured differently
        }

        ArrayList<Unit> apiUnits = apiConnector.getAllUnits();
        apiUnits.forEach(unit -> {
            configConnector.getUnitMappings().put(unit.getName(), unit.getId().toString());
            logger.info("Mapped unit from API: {} -> {}", unit.getName(), unit.getId().toString());
        });

        if (configConnector.getConfigValue("db.units.upload", "API_SYNC_UNITS", "false").equalsIgnoreCase("true")) {
            logger.info("Synchronizing units from API to database...");

            HashSet<String> dbUnits = mariaDBConnector.getUnits();

            for (String dbUnit : dbUnits) {
                logger.info("Checking unit: {}", dbUnit);
                if (apiUnits.stream().noneMatch(u -> u.getName().equals(dbUnit))) {
                    logger.info("Unit '{}' exists in database but not in API; creating in API. ", dbUnit);
                    Unit unit = apiConnector.createUnit(dbUnit);
                    configConnector.getUnitMappings().put(unit.getName(), unit.getId().toString());
                }
            }
        } else {
            logger.info("API unit synchronization is disabled.");
        }

        OutBoxWatcher.start(mariaDBConnector, apiConnector);

    }
}
