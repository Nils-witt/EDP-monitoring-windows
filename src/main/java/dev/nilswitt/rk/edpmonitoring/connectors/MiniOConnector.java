package dev.nilswitt.rk.edpmonitoring.connectors;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.Result;
import io.minio.errors.*;
import io.minio.messages.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class MiniOConnector implements StorageInterface {
    static Logger log = LogManager.getLogger(MiniOConnector.class);
    private final MinioClient minioClient;
    private final String bucketName;

    public MiniOConnector(String endpoint, String bucket, String accessKey, String secretKey) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucketName = bucket;
    }

    public static Map<String, MiniOConnector> getConnectorsFromConfig(ConfigConnector configConnector) {
        Properties props = configConnector.getProps();
        HashMap<String, MiniOConnector> connectors = new HashMap<>();
        HashSet<String> bucketKeys = new HashSet<>();
        props.keySet().stream().filter(key -> key.toString().startsWith("s3.bucket.")).forEach(key -> {
            log.info("Getting bucket key: {}", key);
            String bKey = key.toString().split("\\.")[2];
            bucketKeys.add(bKey);
        });

        for (String bucketKey : bucketKeys) {
            log.info("Initializing MiniOConnector for bucket key: " + bucketKey);
            String id = props.getProperty("s3.bucket." + bucketKey + ".id", null);
            String access_key = props.getProperty("s3.bucket." + bucketKey + ".access_key", null);
            String secret_key = props.getProperty("s3.bucket." + bucketKey + ".secret_key", null);
            String endpoint = props.getProperty("s3.bucket." + bucketKey + ".endpoint", null);
            String bucket = props.getProperty("s3.bucket." + bucketKey + ".bucket", null);
            log.info("INFO MiniOConnector - id: {}, endpoint: {}, bucket: {}, access_key: {}, secret_key {}", id, endpoint, bucket, access_key, secret_key);
            if (id != null && access_key != null && secret_key != null && endpoint != null && bucket != null) {
                MiniOConnector connector = new MiniOConnector(endpoint, bucket, access_key, secret_key);
                connectors.put(id, connector);
            } else {
                log.warn("Skipping MiniOConnector for bucket key: {} due to missing configuration.", bucketKey);
            }
        }

        return connectors;
    }

    public MiniOConnector(ConfigConnector configConnector) {
        String endpoint = configConnector.getConfigValue("s3.endpoint", "S3_ENDPOINT", null);
        String accessKey = configConnector.getConfigValue("s3.access_key", "S3_ACCESS_KEY", null);
        String secretKey = configConnector.getConfigValue("s3.secret_key", "S3_SECRET_KEY", null);
        String bucket = configConnector.getConfigValue("s3.bucket", "S3_BUCKET", null);
        log.info("Using S3 bucket: " + bucket);
        log.info("Using S3 access key: " + accessKey);
        log.info("Using S3 endpoint: " + endpoint);
        log.info("Using S3 secret key: " + secretKey);
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucketName = bucket;
    }

    @Override
    public void putFile(String objectName, String localPath) throws Exception {
        ObjectWriteResponse res = minioClient.uploadObject(
                io.minio.UploadObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .filename(localPath)
                        .build()
        );
        log.info("File uploaded successfully: {} {}", objectName, res.object());
    }

    @Override
    public List<String> getFiles() {
        ArrayList<String> files = new ArrayList<>();
        Iterable<Result<Item>> items = minioClient.listObjects(ListObjectsArgs.builder().bucket(bucketName).build());

        items.forEach(item -> {
            try {
                if (item.get() != null) {
                    try {
                        files.add(item.get().objectName());
                    } catch (Exception e) {
                        log.error("Error retrieving object name: {}", e.getMessage());
                    }
                }
            } catch (ErrorResponseException | NoSuchAlgorithmException | XmlParserException | ServerException |
                     IOException | InvalidKeyException | InvalidResponseException | InternalException |
                     InsufficientDataException e) {
                throw new RuntimeException(e);
            }
        });
        return files;
    }
}
