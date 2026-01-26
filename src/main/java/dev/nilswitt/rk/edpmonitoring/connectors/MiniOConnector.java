package dev.nilswitt.rk.edpmonitoring.connectors;

import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MiniOConnector {
    Logger log = LogManager.getLogger(MiniOConnector.class);
    private final MinioClient minioClient;
    private final String bucketName;

    public MiniOConnector(String endpoint, String bucket, String accessKey, String secretKey) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucketName = bucket;
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

    public void uploadFile(String objectName, String filePath) {
        try {
            ObjectWriteResponse res = minioClient.uploadObject(
                    io.minio.UploadObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .filename(filePath)
                            .build()
            );
            log.info("File uploaded successfully: {} {}", objectName, res.object());
        } catch (Exception e) {
            log.error("Error uploading file: {}", e.getMessage());
        }
    }


}
