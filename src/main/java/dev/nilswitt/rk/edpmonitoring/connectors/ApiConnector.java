package dev.nilswitt.rk.edpmonitoring.connectors;

import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.util.HashMap;

public class ApiConnector {

    private final String apiKey;
    private final String apiUrl;
    private static final Logger LOGGER = LogManager.getLogger(ApiConnector.class);
    private static HashMap<String, String> unitNameAPIIDMap = new HashMap<>();

    public ApiConnector(String apiUrl, String apiKey) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;

        if (unitNameAPIIDMap.isEmpty()) {
            unitNameAPIIDMap.put("ELW", "9af2efeb-d71f-4cb1-98c1-f2a8414bb0e4");
            unitNameAPIIDMap.put("UnitB", "api_id_456");
            unitNameAPIIDMap.put("UnitC", "api_id_789");
        }
    }

    public static String getApiIdForUnitName(String unitName) {
        return unitNameAPIIDMap.get(unitName);
    }

    public boolean testConnection() {
        LOGGER.info("Connecting to API at " + apiUrl + " with key " + apiKey.substring(0, 5) + "*****");

        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.warn("apiUrl is empty or null");
            return false;
        }

        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.warn("Failed to parse media type");
            return false;
        }

        JSONObject json = new JSONObject();
        json.put("token", apiKey);
        RequestBody body = RequestBody.create(json.toJSONString(), mediaType);

        Request request = new Request.Builder()
                .url(apiUrl + "/token/verify/")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            return code >= 200 && code < 300;
        } catch (IOException e) {
            LOGGER.error("Connection test failed", e);
            return false;
        }
    }


    private void setUnitStatus(String unitId, int status) {
        LOGGER.info("Setting status of unit " + unitId + " to " + status);
        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.warn("apiUrl is empty or null");
        }

        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.warn("Failed to parse media type");
        }

        JSONObject json = new JSONObject();
        json.put("unit_status", status);
        RequestBody body = RequestBody.create(json.toJSONString(), mediaType);

        Request request = new Request.Builder()
                .url(apiUrl + "/units/{}/".replace("{}", unitId))
                .patch(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();

            if (code < 200 || code >= 300) {
                LOGGER.error(String.format("Failed to set status of unit %s to %d; Code: %d", unitId, status, code));
                LOGGER.info(response.body().string());
            }
        } catch (IOException e) {
            LOGGER.error("Connection failed", e);
        }
    }


    public String getApiUrl() {
        return apiUrl;
    }

    public void processOutboxRow(MariaDBConnector.WorkerOutbox row) {
        LOGGER.info("Processing outbox row " + row.id);
        String payload = row.payload;
        String unitPK = row.pk;
        JSONParser parser = new JSONParser();
        try {
            JSONObject json = (JSONObject) parser.parse(payload);
            System.out.println("Parsed JSON: " + json.toJSONString());

            if (json.containsKey("NEW_STATUS")) {
                LOGGER.info("Found NEW_STATUS in payload for outbox row " + row.id);
                int newStatus = Integer.parseInt(json.get("NEW_STATUS").toString());
                LOGGER.info("New status: " + newStatus);
                setUnitStatus(getApiIdForUnitName(unitPK), newStatus);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to parse JSON payload for outbox row " + row.id, e);
        }


    }
}
