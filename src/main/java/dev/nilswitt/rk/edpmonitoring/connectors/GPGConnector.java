package dev.nilswitt.rk.edpmonitoring.connectors;


import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pgpainless.sop.SOPImpl;
import sop.SOP;

public class GPGConnector {

    private static final Logger log = LogManager.getLogger(GPGConnector.class);

    public static void encrypt(String publicKeyPath, String inputFilePath, String outputFilePath) throws IOException {
        if (Files.notExists(Paths.get(publicKeyPath)) || Files.notExists(Paths.get(inputFilePath))) {
            log.error("Public key or input file does not exist. Public Key: {}, Input File: {}", publicKeyPath, inputFilePath);
            return;
        }
        log.info("Starting encryption of file: {} with {} to {}", inputFilePath, publicKeyPath, outputFilePath);
        SOP sop = new SOPImpl();
        log.info("Encrypting file: {}", inputFilePath);
        try {
            sop.encrypt().withCert(new FileInputStream(publicKeyPath)).plaintext(new FileInputStream(inputFilePath)).writeTo(new FileOutputStream(outputFilePath));

        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage(), e);
            return;
        }
        log.info("File encrypted successfully: {}", outputFilePath);
    }
}
