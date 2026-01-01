package dev.nilswitt.rk.edpmonitoring;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Utilities {

    /**
     * Return the current working directory as an absolute, normalized Path.
     */
    public static Path getCurrentWorkingDirectory() {
        String WKD = System.getenv("WORK_DIR");
        if (WKD != null && !WKD.isEmpty()) {
            return Paths.get(WKD).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

}
