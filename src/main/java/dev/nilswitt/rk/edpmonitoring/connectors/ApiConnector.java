package dev.nilswitt.rk.edpmonitoring.connectors;

import dev.nilswitt.rk.edpmonitoring.enitites.Unit;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.util.ArrayList;

public class ApiConnector {

    private final String apiKey;
    private final String apiUrl;
    private static final Logger LOGGER = LogManager.getLogger(ApiConnector.class);
    private final ConfigConnector configConnector;

    public ApiConnector(String apiUrl, String apiKey, ConfigConnector configConnector) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;

        this.configConnector = configConnector;
    }

    public String getApiIdForUnitName(String unitName) {
        return configConnector.getUnitMappings().get(unitName);
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


    public ArrayList<Unit> getAllUnits() {
        ArrayList<Unit> units = new ArrayList<>();
        LOGGER.info("Getting all units");

        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.warn("apiUrl is empty or null");
        }

        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.warn("Failed to parse media type");
        }


        Request request = new Request.Builder()
                .url(apiUrl + "/units/")
                .get()
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            String responseBody = response.body().string();
            LOGGER.info(responseBody);
            if (code < 200 || code >= 300) {
                LOGGER.error(String.format("Failed to get units; Code: %d", code));

            }
            JSONArray jsonArray = (JSONArray) new JSONParser().parse(responseBody);
            for (Object o : jsonArray) {
                JSONObject unitJson = (JSONObject) o;
                Unit unit = Unit.of(unitJson.toJSONString());
                units.add(unit);
            }
        } catch (IOException e) {
            LOGGER.error("Connection failed", e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        return units;
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

    public Unit createUnit(String name) {
        LOGGER.info("Creating unit " + name);
        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.warn("apiUrl is empty or null");
        }

        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.warn("Failed to parse media type");
        }

        JSONObject json = new JSONObject();
        json.put("name", name);
        RequestBody body = RequestBody.create(json.toJSONString(), mediaType);

        Request request = new Request.Builder()
                .url(apiUrl + "/units/")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();

            if (code < 200 || code >= 300) {
                LOGGER.error("Failed Code: {}", code);
                LOGGER.info(response.body().string());
                throw new RuntimeException("Failed to create unit; Code: " + code);
            }
            return Unit.of(response.body().string());
        } catch (IOException e) {
            LOGGER.error("Connection failed", e);
            throw new RuntimeException(e);
        }
    }
}
