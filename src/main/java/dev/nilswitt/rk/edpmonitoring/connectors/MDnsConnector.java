package dev.nilswitt.rk.edpmonitoring.connectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;

public class MDnsConnector {
    private static final Logger log = LogManager.getLogger(MDnsConnector.class);
    private static JmDNS jmdns;

    public static void start() {
        try {
            // Create a JmDNS instance
            jmdns = JmDNS.create(InetAddress.getLocalHost());

            ServiceInfo serviceInfo = ServiceInfo.create("_iuk._tcp.local.", "edp-monitor", 8081, "path=/health");
            jmdns.registerService(serviceInfo);
            log.info("JmDNS started {}",  serviceInfo.getNiceTextString());

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(MDnsConnector::stop));
    }

    public static void stop() {
        if (jmdns != null) {
            jmdns.unregisterAllServices();
            log.info("JmDNS stopped");
        }

    }
}
