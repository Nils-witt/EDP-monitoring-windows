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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    public static void main(String[] args) {

        LOGGER.info("Working Directory: {}", Utilities.getCurrentWorkingDirectory());
        LOGGER.info("ARGS: {}", String.join(" ", args));

        Optional<String> wkdArg = Arrays.stream(args).filter(u -> u.startsWith("WORK_DIR=")).findFirst();
        if (wkdArg.isPresent()) {
            String wkdPath = wkdArg.get().substring("WORK_DIR=".length());
            System.setProperty("user.dir", wkdPath);
            LOGGER.info("Set working directory from argument: {}", wkdPath);
        } else {
            LOGGER.info("No WORK_DIR argument provided; using default working directory.");
        }

        ConfigConnector configConnector = ConfigConnector.getInstance();

        String apiUrl = configConnector.getConfigValue("api.url", "API_URL", "http://localhost:8080/api");
        String apiToken = configConnector.getConfigValue("api.token", "API_TOKEN", null);
        String apiUsername = configConnector.getConfigValue("api.username", "API_TOKEN", null);
        String apiPassword = configConnector.getConfigValue("api.password", "API_TOKEN", null);

        LOGGER.info("Using API URL: {}", apiUrl);

        if (apiToken == null || apiToken.isEmpty()) {
            LOGGER.info("No API TOKEN provided; using credentials for login.");
        } else {
            LOGGER.info("Using API Token: {}", apiToken.substring(0, 5));
        }
        ApiConnector apiConnector = new ApiConnector(apiUrl, apiToken, apiUsername, apiPassword, configConnector);
        if (apiConnector.testConnection()) {
            LOGGER.info("API connection test successful.");
        } else {
            LOGGER.error("API connection test failed. Exiting.");
            System.exit(1);
        }

        String url = configConnector.getConfigValue("db.url", "DB_URL", "jdbc:mariadb://localhost:3306/edp_monitoring");
        String user = configConnector.getConfigValue("db.user", "DB_USER", "edp_user");
        String pass = configConnector.getConfigValue("db.password", "DB_PASSWORD", "edp_password");

        LOGGER.info("Using DB URL: {} (user={})", url, user);
        MariaDBConnector mariaDBConnector = new MariaDBConnector(url, user, pass);
        if (mariaDBConnector.testConnection()) {
            LOGGER.info("Database connection test successful.");
        } else {
            LOGGER.error("Database connection test failed. Exiting.");
            System.exit(1);
        }

        try {
            Path logs = Paths.get(System.getProperty("user.home"), "logs");
            if (!Files.exists(logs)) {
                Files.createDirectories(logs);
                LOGGER.info("Created logs directory: {}", logs.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to create logs directory: " + e.getMessage());
            e.printStackTrace(System.err);
            // continue; logging may still work if directory exists or if configured differently
        }

        ArrayList<Unit> apiUnits = apiConnector.getAllUnits();
        apiUnits.forEach(unit -> {
            configConnector.getUnitMappings().put(unit.getName(), unit.getId().toString());
        });

        if (configConnector.getConfigValue("db.units.upload", "API_SYNC_UNITS", "false").equalsIgnoreCase("true")) {
            LOGGER.info("Synchronizing units from API to database...");

            HashSet<Unit> dbUnits = mariaDBConnector.getUnits();

            for (Unit dbUnit : dbUnits) {
                LOGGER.info("Checking unit: ({})", dbUnit.getName());
                if (apiUnits.stream().noneMatch(u -> u.getName().equals(dbUnit.getName()))) {
                    LOGGER.info("Unit '{}' exists in database but not in API; creating in API. ", dbUnit);
                    Unit unit = apiConnector.createUnit(dbUnit);
                    configConnector.getUnitMappings().put(unit.getName(), unit.getId().toString());
                }else{
                    apiUnits.stream().filter(u -> u.getName().equals(dbUnit.getName())).findFirst().ifPresent(u -> {
                        LOGGER.info("Unit '{}' exists in both database and API; skipping creation.", dbUnit.getName());
                        dbUnit.setId(u.getId());
                        apiConnector.updateUnit(dbUnit);
                    });
                }
            }
        } else {
            LOGGER.info("API unit synchronization is disabled.");
        }

        OutBoxWatcher.start(mariaDBConnector, apiConnector);

    }
}
