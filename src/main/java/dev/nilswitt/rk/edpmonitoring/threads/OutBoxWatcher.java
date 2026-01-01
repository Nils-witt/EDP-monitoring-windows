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
    private static final Logger logger = LogManager.getLogger(OutBoxWatcher.class);
    private static MariaDBConnector mariaDBConnector;
    private static ApiConnector apiConnector;

    @Override
    public void run() {
        ArrayList<MariaDBConnector.WorkerOutbox> rows = mariaDBConnector.getWorkerOutbox();
        logger.debug("Fetched {} rows from worker_outbox:", rows.size());
        for (MariaDBConnector.WorkerOutbox row : rows) {
            logger.info("Outbox ID: {}, PK: {}, Payload: {}, Created At: {}, Status: {}, Correlation ID: {}",
                    row.id, row.pk, row.payload, row.createdAt, row.status, row.correlationId);
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
            logger.info("Shutdown requested; stopping executor...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("Executor did not terminate within timeout; forcing shutdown.");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for executor to terminate", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }

    public static void stop() {
        executor.shutdown();
    }
}
