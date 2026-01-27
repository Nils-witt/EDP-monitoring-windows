package dev.nilswitt.rk.edpmonitoring.services;

import dev.nilswitt.rk.edpmonitoring.connectors.ConfigConnector;
import dev.nilswitt.rk.edpmonitoring.connectors.MiniOConnector;
import dev.nilswitt.rk.edpmonitoring.connectors.StorageInterface;

import java.util.HashMap;
import java.util.Map;

public class StorageService {

    private static StorageService instance;
    private final Map<String, StorageInterface> connectors = new HashMap<>();
    private StorageService() {
        initMiniOConnectors();

    }

    public Map<String, StorageInterface> getConnectors() {
        return connectors;
    }

    private void initMiniOConnectors() {
        Map<String,MiniOConnector> cns = MiniOConnector.getConnectorsFromConfig(ConfigConnector.getInstance());
        for (Map.Entry<String, MiniOConnector> entry : cns.entrySet()) {
            if(connectors.containsKey(entry.getKey())) {
                // Log warning about duplicate key
                System.out.println("Warning: Duplicate storage connector key found: " + entry.getKey() + ". Skipping this connector.");
            } else {
                connectors.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public static StorageService getInstance() {
        if (instance == null) {
            instance = new StorageService();
        }
        return instance;
    }


}
