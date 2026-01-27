package dev.nilswitt.rk.edpmonitoring.connectors;

import java.util.List;

public interface StorageInterface {

    void putFile(String objectName, String localPath) throws Exception;

    List<String> getFiles();
}
