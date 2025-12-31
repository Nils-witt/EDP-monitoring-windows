package dev.nilswitt.rk.edpmonitoring;

import java.sql.*;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        Properties cfg = loadConfig();

        String url = getConfigValue(cfg, "db.url", "DB_URL", "jdbc:mariadb://localhost:3306/edp_monitoring");
        String user = getConfigValue(cfg, "db.user", "DB_USER", "edp_user");
        String pass = getConfigValue(cfg, "db.password", "DB_PASSWORD", "edp_password");

        logger.info("Using DB URL: {} (user={})", url, user);

        // Ensure logs directory exists so the file appender can write to it
        try {
            Path logs = Paths.get("logs");
            if (!Files.exists(logs)) {
                Files.createDirectories(logs);
                logger.info("Created logs directory: {}", logs.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to create logs directory: " + e.getMessage());
            e.printStackTrace(System.err);
            // continue; logging may still work if directory exists or if configured differently
        }

        Runnable helloRunnable = () -> getDBRows(url, user, pass);

        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(helloRunnable, 0, 3, TimeUnit.SECONDS);

        // Ensure the executor is shut down on JVM exit
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

    private static Properties loadConfig() {
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
            logger.info("No config.properties found in working directory; using environment variables or defaults.");
        }
        return props;
    }

    private static String getConfigValue(Properties props, String key, String envKey, String defaultVal) {
        String v = props.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        String ev = System.getenv(envKey);
        if (ev != null && !ev.isEmpty()) return ev;
        return defaultVal;
    }

    private static void getDBRows(String url, String user, String pass) {

        String query = "SELECT id,pk,payload,created_at,status,correlation_id FROM webhook_outbox WHERE status = 'NEW'";

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            // Print header
            StringBuilder header = new StringBuilder();
            for (int i = 1; i <= cols; i++) {
                header.append(meta.getColumnLabel(i));
                if (i < cols) header.append(" | ");
            }
            logger.info(header.toString());

            // Print separator
            StringBuilder sep = new StringBuilder();
            for (int i = 0; i < Math.max(header.length(), 0); i++) sep.append('-');
            logger.info(sep.toString());

            boolean any = false;
            while (rs.next()) {
                any = true;
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    Object val = rs.getObject(i);
                    row.append(val != null ? val.toString() : "NULL");
                    if (i < cols) row.append(" | ");
                }
                logger.info(row.toString());
            }

            if (!any) logger.info("[no rows]");

        } catch (SQLException e) {
            logger.error("Database error: {}", e.getMessage(), e);
            System.exit(2);
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            System.exit(3);
        }
    }
}
