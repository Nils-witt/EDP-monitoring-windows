package dev.nilswitt.rk.edpmonitoring;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Utilities {

    private static final Logger LOGGER = LogManager.getLogger(Utilities.class);

    /**
     * Return the current working directory as an absolute, normalized Path.
     */
    public static Path getCurrentWorkingDirectory() {
        String WKD = System.getenv("WORK_DIR");
        if (WKD != null && !WKD.isEmpty()) {
            LOGGER.debug("Using WORK_DIR environment variable: {}", WKD);
            return Paths.get(WKD).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

}
