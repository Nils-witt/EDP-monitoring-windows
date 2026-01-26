package dev.nilswitt.rk.edpmonitoring.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.nilswitt.rk.edpmonitoring.connectors.ConfigConnector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class HealthService {

    private static final Logger log = LogManager.getLogger(HealthService.class);

    private static Map<String, Object> statusMap = new HashMap<>();

    public static void start(ConfigConnector configConnector) {
        try {
            InetSocketAddress address = new InetSocketAddress(8080);
            HttpHandler handler = new HealthHandler(configConnector);

            HttpServer server = HttpServer.create(address, 0, "/", handler);
            server.start();
            log.info("Health service started: http://localhost:8080/health");
        } catch (Exception e) {
            log.error("Failed to start Expose Health service: {}", e.getMessage(), e);
        }
    }

    private static class HealthHandler implements HttpHandler {
        ConfigConnector configConnector;

        public HealthHandler(ConfigConnector configConnector) {
            this.configConnector = configConnector;
        }

        private String getStatus() throws JsonProcessingException {
            ObjectMapper mapper = new ObjectMapper();
            String jsonResult = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(statusMap);
            return jsonResult;
        }

        @Override
        public void handle(HttpExchange exchange) {
            try {
                String response = getStatus();
                exchange.sendResponseHeaders(200, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
            } catch (Exception e) {
                log.error("Error handling health check request: {}", e.getMessage(), e);
            }
        }
    }

    public static Map<String, Object> getStatusMap() {
        return statusMap;
    }

    public static void updateStatusKey(String key, Object value) {
        statusMap.put(key, value);
    }
}
