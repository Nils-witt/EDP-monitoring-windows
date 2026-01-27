package dev.nilswitt.rk.edpmonitoring.services;

import dev.nilswitt.rk.edpmonitoring.connectors.ConfigConnector;
import dev.nilswitt.rk.edpmonitoring.connectors.GPGConnector;
import dev.nilswitt.rk.edpmonitoring.connectors.StorageInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
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
    }


    public static void start() {
        ConfigConnector configConnector = ConfigConnector.getInstance();
        Properties props = configConnector.getProps();
        HashSet<String> backupKeys = new HashSet<>();
        props.keySet().stream().filter(key -> key.toString().startsWith("db.backup.")).forEach(key -> {
            LOGGER.info("Getting backup key: {}", key);
            String bKey = key.toString().split("\\.")[2];
            backupKeys.add(bKey);
        });
        backupKeys.forEach(key -> {
            String intervalStr = props.getProperty("db.backup." + key + ".interval", null);
            String enabledStr = props.getProperty("db.backup." + key + ".enabled", "false");
            String storageId = props.getProperty("db.backup." + key + ".storage_id", null);
            String tmpDir = props.getProperty("db.backup." + key + ".tmpdir", null);
            String id = props.getProperty("db.backup." + key + ".id", null);
            String executablePath = props.getProperty("db.backup." + key + ".dump_executable", null);
            String encryptionKeyPath = props.getProperty("db.backup." + key + ".encryption_Key", null);
            if (enabledStr != null && enabledStr.equals("true")) {
                if (intervalStr == null || storageId == null || id == null || executablePath == null || encryptionKeyPath == null || tmpDir == null) {
                    LOGGER.error("Backup configuration for key {} is incomplete. Skipping backup task.", key);
                    return;
                }
                StorageInterface storageInterface = StorageService.getInstance().getConnectors().get(storageId);
                startWorker(id, Integer.parseInt(intervalStr) * 60, storageInterface, executablePath, tmpDir, encryptionKeyPath);
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdown));
    }

    private static void startWorker(String id, int interval, StorageInterface storageInterface, String executablePath, String tmpDir, String encryptionKeyPath) {
        LOGGER.info("Starting BackUpWorker with ID: {}, Interval: {} seconds, Executable Path: {}, Temp Dir: {}, Encryption Key Path: {}", id, interval, executablePath, tmpDir, encryptionKeyPath);
        HashSet<String> backupKeys = new HashSet<>();
        String prefix = "backup_" + id + "_";
        storageInterface.getFiles().stream().filter(file -> file.startsWith(prefix)).forEach(file -> backupKeys.add(file.substring(prefix.length(), prefix.length() + 19)));
        LocalDateTime last_backup = null;
        for (String backupKey : backupKeys) {
            LocalDateTime keyTime = LocalDateTime.parse(backupKey, DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            if (last_backup == null) {
                last_backup = keyTime;
            } else if (keyTime.isAfter(last_backup)) {
                last_backup = keyTime;
            }
        }
        LOGGER.info("Last backup time for ID {}: {}", id, last_backup);

        int duration_ago = Math.toIntExact(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - (last_backup != null ? last_backup.toEpochSecond(ZoneOffset.UTC) : 0));
        LOGGER.info("Last run {} seconds ago", duration_ago);

        int offset = 0;
        if (duration_ago < interval) {
            LOGGER.info("Next BackUpWorker for ID {} scheduled in {} seconds", id, interval - duration_ago);
            offset = interval - duration_ago;
        }else {
            LOGGER.info("Scheduling immediate BackUpWorker for ID {}", id);
        }

        executor.scheduleAtFixedRate(new BackUpWorker(id, storageInterface, executablePath, tmpDir, encryptionKeyPath), offset, interval, TimeUnit.SECONDS);
    }

    public static void stop() {
        executor.shutdown();
        statusMap.put("running", false);
    }

    private static class BackUpWorker implements Runnable {
        private static Logger logger = LogManager.getLogger(BackUpWorker.class);
        private static boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        private static DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

        private final String executablePath;
        private final StorageInterface storageInterface;
        private final String tmpDir;
        private final String encryptionKeyPath;
        private final String id;

        Path backTmpFile;

        public BackUpWorker(String id, StorageInterface storageInterface, String executablePath, String tmpDir, String encryptionKeyPath) {
            this.storageInterface = storageInterface;
            this.executablePath = executablePath;
            this.tmpDir = tmpDir;
            this.encryptionKeyPath = encryptionKeyPath;
            this.id = id;
        }

        @Override
        public void run() {
            logger.info("BackUpWorker started.");
            try {
                runPreHook();
                uploadToStorage();
                runPostHook();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            logger.info("BackUpWorker finished.");
        }


        private void runPreHook() throws IOException, InterruptedException {
            logger.info("Pre-hook started.");
            Random random = new Random();
            if (executablePath == null) {
                logger.error("No database dump executable path configured. Aborting backup.");
                return;
            }
            String backupFileName = random.ints(48, 122 + 1)
                    .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                    .limit(10)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString() + ".sql";
            backTmpFile = Path.of(tmpDir, backupFileName);
            Path sqlDir = Path.of(tmpDir);
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

        private void uploadToStorage() {
            logger.info("uploadToMinIO started.");
            try {
                GPGConnector.encrypt(encryptionKeyPath, backTmpFile.toAbsolutePath().toString(), backTmpFile.toAbsolutePath() + ".gpg");
                logger.info("File encryption finished.");
                storageInterface.putFile("backup_" + id + "_" + LocalDateTime.now().format(myFormatObj) + ".sql.gpg", backTmpFile.toAbsolutePath() + ".gpg");
                logger.info("Upload finished.");
                BackUpService.updateStatus(id + "_last_backup", LocalDateTime.now().format(myFormatObj));
            } catch (Exception e) {
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
            File fileGPG = new File(backTmpFile.toAbsolutePath() + ".gpg");
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
