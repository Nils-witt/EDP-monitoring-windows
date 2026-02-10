package dev.nilswitt.rk.edpmonitoring;

import dev.nilswitt.rk.edpmonitoring.connectors.ApiConnector;
import dev.nilswitt.rk.edpmonitoring.connectors.ConfigConnector;
import dev.nilswitt.rk.edpmonitoring.connectors.MDnsConnector;
import dev.nilswitt.rk.edpmonitoring.connectors.MariaDBConnector;
import dev.nilswitt.rk.edpmonitoring.enitites.Unit;
import dev.nilswitt.rk.edpmonitoring.services.BackUpService;
import dev.nilswitt.rk.edpmonitoring.services.HealthService;
import dev.nilswitt.rk.edpmonitoring.threads.OutBoxWatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger(Main.class);
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) throws IOException {
        LOGGER.info("Working Directory: {}", Utilities.getCurrentWorkingDirectory());
        LOGGER.info("ARGS: {}", String.join(" ", args));

        Main main = new Main();
        main.startUp();
    }

    public void startUp() {

        ConfigConnector configConnector = ConfigConnector.getInstance();

        if (configConnector.getConfigValue("health.enabled", "HEALTH_ENABLED", "false").equalsIgnoreCase("true")) {
            HealthService.start(configConnector);
        }
        if (configConnector.getConfigValue("mdns.enabled", "MDNS_ENABLED", "false").equalsIgnoreCase("true")) {
            MDnsConnector.start();
        }
        BackUpService.start();

        MariaDBConnector mariaDBConnector = new MariaDBConnector(configConnector);
        configConnector.setMariaDBConnector(mariaDBConnector);

        initApiLiveConnection();
    }

    private void initApiLiveConnection() {
        try {
            ConfigConnector configConnector = ConfigConnector.getInstance();
            if (configConnector.getConfigValue("api.enabled", "API_ENABLED", "false").equalsIgnoreCase("true")) {
                ApiConnector apiConnector = new ApiConnector(configConnector);
                if (apiConnector.testConnection()) {
                    LOGGER.info("API connection test successful.");
                } else {
                    LOGGER.error("API connection test failed.");
                    throw new RuntimeException("API connection test failed. Aborting.");
                }
                configConnector.setApiConnector(apiConnector);

                ArrayList<Unit> apiUnits = apiConnector.getAllUnits();
                apiUnits.forEach(unit -> {
                    configConnector.getUnitMappings().put(unit.getName(), unit.getId());
                    configConnector.getUnits().put(unit.getId(), unit);
                });
                LOGGER.info("Found {} units in API.", apiUnits.size());
                if (configConnector.getConfigValue("db.units.upload", "API_SYNC_UNITS", "false").equalsIgnoreCase("true")) {
                    LOGGER.info("Synchronizing units from API to database...");

                    HashSet<Unit> dbUnits = configConnector.getMariaDBConnector().getUnits();

                    for (Unit dbUnit : dbUnits) {
                        LOGGER.info("Checking unit: ({})", dbUnit.getName());
                        if (apiUnits.stream().noneMatch(u -> u.getName().equals(dbUnit.getName()))) {
                            LOGGER.info("Unit '{}' exists in database but not in API; creating in API. ", dbUnit);
                            Unit unit = apiConnector.createUnit(dbUnit);
                            configConnector.getUnitMappings().put(unit.getName(), unit.getId());
                            configConnector.getUnits().put(unit.getId(), unit);
                        } else {
                            apiUnits.stream().filter(u -> u.getName().equals(dbUnit.getName())).findFirst().ifPresent(unit -> {
                                LOGGER.info("Unit '{}' exists in both database and API; skipping creation.", dbUnit.getName());
                                configConnector.getUnits().put(unit.getId(), unit);
                                configConnector.getUnitMappings().put(unit.getName(), unit.getId());

                                unit.setStatus(dbUnit.getStatus());
                                unit.setPosition(dbUnit.getPosition());
                                apiConnector.updateUnit(unit);
                            });
                        }
                    }
                } else {
                    LOGGER.info("API unit synchronization is disabled.");
                }
            }

            if (configConnector.getConfigValue("db.liveconnection.enabled", "DB_LIVECONNECTION_ENABLED", "false").equalsIgnoreCase("true")) {
                OutBoxWatcher.start(configConnector.getMariaDBConnector(), configConnector.getApiConnector());
            }
        } catch (Exception e) {
            LOGGER.error("Error during startup. retrying in 60 seconds", e);
            executor.schedule(this::initApiLiveConnection, 60, TimeUnit.SECONDS);
        }

    }
}
