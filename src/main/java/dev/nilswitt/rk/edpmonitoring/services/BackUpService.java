package dev.nilswitt.rk.edpmonitoring.services;

import dev.nilswitt.rk.edpmonitoring.connectors.ConfigConnector;
import dev.nilswitt.rk.edpmonitoring.connectors.GPGConnector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BackUpService {
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private static final Logger LOGGER = LogManager.getLogger(BackUpService.class);
    private static Map<String, Object> statusMap = new ConcurrentHashMap<>();

    public static Map<String, Object> getStatusMap() {
        return statusMap;
    }
    public static void updateStatus(String key, Object value) {
        statusMap.put(key, value);
        HealthService.updateStatusKey("backup_service", statusMap);
    }

    public static void start(ConfigConnector configConnector) {
        if (configConnector.getMiniOConnector() == null) {
            LOGGER.error("MiniOConnector is not initialized. Backup service will not start.");
            return;
        }
        executor.scheduleAtFixedRate(new BackUpWorker(configConnector), 0, 5, TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(Integer.parseInt(configConnector.getConfigValue("db.backup.interval", "DB_BACKUP_INTERVAL", "5")), TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
        updateStatus("running", true);
    }

    public static void stop() {
        executor.shutdown();
        statusMap.put("running", false);
    }

    private static class BackUpWorker implements Runnable {
        Logger logger = LogManager.getLogger(BackUpWorker.class);
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
        private final ConfigConnector configConnector;
        Path backTmpFile;
        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

        public BackUpWorker(ConfigConnector configConnector) {
            this.configConnector = configConnector;
        }

        @Override
        public void run() {
            logger.info("BackUpWorker started.");
            try {
                runPreHook();
                uploadToMinIO();
                runPostHook();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            logger.info("BackUpWorker finished.");
        }


        private void runPreHook() throws IOException, InterruptedException {
            logger.info("Pre-hook started.");
            Random random = new Random();
            String executablePath = configConnector.getConfigValue("db.backup.dump_executable", "DB_PATH_DUMP_EXECUTABLE", null);
            if (executablePath == null) {
                logger.error("No database dump executable path configured. Aborting backup.");
                return;
            }
            String sqlPath = configConnector.getConfigValue("db.backup.dump_tmp", "DB_PATH_DUMP_FILE", "tmp");
            String backupFileName = random.ints(48, 122 + 1)
                    .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                    .limit(10)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString() + ".sql";
            backTmpFile = Path.of(sqlPath, backupFileName);
            Path sqlDir = Path.of(sqlPath);
            logger.info("Back-up executable path: {}", executablePath);
            logger.info("Back-up tmp path: {}", backTmpFile);
            if (!Files.exists(sqlDir)) {
                Files.createDirectories(sqlDir);
            }

            if (isWindows) {
                ProcessBuilder builder = new ProcessBuilder("CMD", "/C", executablePath + " > " + backTmpFile.toAbsolutePath());
                logger.info("Running: {}", String.join(" ", builder.command()));
                builder.inheritIO();
                Process process = builder.start();
                process.waitFor();
            } else {
                ProcessBuilder builder = new ProcessBuilder("sh", "-c", executablePath + " > " + backTmpFile.toAbsolutePath());
                builder.inheritIO();
                Process process = builder.start();
                process.waitFor();
            }
            logger.info("Pre-hook finished.");
        }

        private void uploadToMinIO() {
            logger.info("uploadToMinIO started.");
            try {

                String gpgPublicKeyPath = configConnector.getConfigValue("s3.encryption.key", "S3_ENCRYPTION_KEY", null);
                GPGConnector.encrypt(gpgPublicKeyPath, backTmpFile.toAbsolutePath().toString(), backTmpFile.toAbsolutePath() + ".gpg");
                logger.info("File encryption finished.");
                configConnector.getMiniOConnector().uploadFile(LocalDateTime.now().format(myFormatObj) + ".sql.gpg", backTmpFile.toAbsolutePath().toString()+ ".gpg");
                logger.info("MiniO connector upload finished.");
                BackUpService.updateStatus("last_backup",LocalDateTime.now().format(myFormatObj));
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
            logger.info("uploadToMinIO finished.");
        }

        private void runPostHook() {
            logger.info("Post-hook started.");
            File file = new File(backTmpFile.toAbsolutePath().toString());
            if (file.exists()) {
                if (file.delete()) {
                    logger.info("Temporary backup file deleted: {}", backTmpFile.toAbsolutePath());
                } else {
                    logger.warn("Failed to delete temporary backup file: {}", backTmpFile.toAbsolutePath());
                }
            }
            File fileGPG = new File(backTmpFile.toAbsolutePath().toString() + ".gpg");
            if (fileGPG.exists()) {
                if (fileGPG.delete()) {
                    logger.info("Temporary backup file deleted: {}", backTmpFile.toAbsolutePath() + ".gpg");
                } else {
                    logger.warn("Failed to delete temporary backup file: {}", backTmpFile.toAbsolutePath() + ".gpg");
                }
            }
            logger.info("Post-hook finished.");
        }
    }


}
