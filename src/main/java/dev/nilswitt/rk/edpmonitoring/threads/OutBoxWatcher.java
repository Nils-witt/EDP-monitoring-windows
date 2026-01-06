package dev.nilswitt.rk.edpmonitoring.threads;

import dev.nilswitt.rk.edpmonitoring.connectors.ApiConnector;
import dev.nilswitt.rk.edpmonitoring.connectors.MariaDBConnector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OutBoxWatcher implements Runnable {

    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private static final Logger LOGGER = LogManager.getLogger(OutBoxWatcher.class);
    private static MariaDBConnector mariaDBConnector;
    private static ApiConnector apiConnector;

    @Override
    public void run() {
        ArrayList<MariaDBConnector.WorkerOutbox> rows = mariaDBConnector.getWorkerOutbox();
        for (MariaDBConnector.WorkerOutbox row : rows) {
            apiConnector.processOutboxRow(row);
            mariaDBConnector.removeFromOutbox(row.id);
        }
    }

    public static void start(MariaDBConnector mariaDBConnector, ApiConnector apiConnector) {
        if (mariaDBConnector == null) {
            throw new IllegalArgumentException("MariaDBConnector cannot be null");
        }
        if (apiConnector == null) {
            throw new IllegalArgumentException("ApiConnector cannot be null");
        }
        OutBoxWatcher.mariaDBConnector = mariaDBConnector;
        OutBoxWatcher.apiConnector = apiConnector;
        executor.scheduleAtFixedRate(new OutBoxWatcher(), 0, 3, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }

    public static void stop() {
        executor.shutdown();
    }
}
