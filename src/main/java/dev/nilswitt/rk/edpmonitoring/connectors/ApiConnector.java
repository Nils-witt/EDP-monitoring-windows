package dev.nilswitt.rk.edpmonitoring.connectors;

import dev.nilswitt.rk.edpmonitoring.enitites.LngLat;
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
import java.util.UUID;

public class ApiConnector {

    private String apiKey;
    private final String apiUrl;
    private final String username;
    private final String password;
    private static final Logger LOGGER = LogManager.getLogger(ApiConnector.class);
    private final ConfigConnector configConnector;

    public ApiConnector(String apiUrl, String apiKey, String username, String password, ConfigConnector configConnector) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.username = username;
        this.password = password;

        this.configConnector = configConnector;
    }

    public String getApiIdForUnitName(String unitName) {
        return configConnector.getUnitMappings().get(unitName);
    }

    public boolean testConnection() {
        LOGGER.info("Connecting to API at " + apiUrl);

        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.warn("apiUrl is empty or null");
            return false;
        }

        if (this.apiKey == null || this.apiKey.isEmpty()) {
            this.login();
        }
        if (this.apiKey == null || this.apiKey.isEmpty()) {
            LOGGER.warn("apiKey is empty or null");
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

    public String login() {
        if (this.apiUrl == null || this.apiUrl.isEmpty()) {
            LOGGER.warn("apiUrl is empty or null");
            return null;
        }

        LOGGER.info("Logging in to API at " + this.apiUrl + " with username " + this.username);

        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.warn("Failed to parse media type");
            return null;
        }

        JSONObject json = new JSONObject();
        json.put("username", this.username);
        json.put("password", this.password);
        RequestBody body = RequestBody.create(json.toJSONString(), mediaType);

        Request request = new Request.Builder()
                .url(this.apiUrl + "/token/")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            if (code >= 200 && code < 300) {
                String res = response.body().string();
                JSONObject resJson = (JSONObject) new JSONParser().parse(res);
                String token = (String) resJson.get("access");
                LOGGER.info("Login successful, obtained token");
                this.apiKey = token;
                return token;
            }
            return null;
        } catch (IOException | ParseException e) {
            LOGGER.error("Connection test failed", e);
            return null;
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

            if (code == 401) {
                LOGGER.info("Unauthorized, trying to login again");
                this.login();
                setUnitStatus(unitId, status);
                return;
            }
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
            if (code == 401) {
                LOGGER.info("Unauthorized, trying to login again");
                this.login();
                return getAllUnits();
            }
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
            Unit unit = new Unit(UUID.fromString(getApiIdForUnitName(unitPK)), unitPK);
            JSONObject json = (JSONObject) parser.parse(payload);
            System.out.println("Parsed JSON: " + json.toJSONString());
            if (json.containsKey("NEW_STATUS") && json.containsKey("OLD_STATUS")) {
                if (!json.get("OLD_STATUS").toString().equalsIgnoreCase(json.get("NEW_STATUS").toString())) {
                    LOGGER.info("Found NEW_STATUS in payload for outbox row " + row.id);
                    int newStatus = Integer.parseInt(json.get("NEW_STATUS").toString());
                    unit.setStatus(newStatus);
                }
            }
            if (json.containsKey("OLD_KOORDX") && json.containsKey("NEW_KOORDX") && json.containsKey("OLD_KOORDY") && json.containsKey("NEW_KOORDY")) {
                if (!json.get("OLD_KOORDX").toString().equalsIgnoreCase(json.get("NEW_KOORDX").toString()) ||
                        !json.get("OLD_KOORDY").toString().equalsIgnoreCase(json.get("NEW_KOORDY").toString())) {
                    LOGGER.info("Found coordinate change in payload for outbox row " + row.id);
                    double newLat = Double.parseDouble(json.get("NEW_KOORDY").toString());
                    double newLng = Double.parseDouble(json.get("NEW_KOORDX").toString());
                    LOGGER.info("New coordinates: " + newLat + ", " + newLng);
                    unit.setPosition(new LngLat(newLng, newLat));
                }
            }

            updateUnit(unit);

        } catch (Exception e) {
            LOGGER.error("Failed to parse JSON payload for outbox row " + row.id, e);
        }


    }

    public Unit createUnit(Unit unit) {
        LOGGER.info("Creating unit " + unit.getName());
        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.warn("apiUrl is empty or null");
        }

        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.warn("Failed to parse media type");
        }

        JSONObject json = new JSONObject();
        json.put("name", unit.getName());
        if (unit.getPosition() != null) {
            json.put("latitude", unit.getPosition().getLatitude());
            json.put("longitude", unit.getPosition().getLongitude());
        }
        if (unit.getStatus() >= 0 && unit.getStatus() < 10) {
            json.put("unit_status", unit.getStatus());
        }
        RequestBody body = RequestBody.create(json.toJSONString(), mediaType);

        Request request = new Request.Builder()
                .url(apiUrl + "/units/")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();

            if (code == 401) {
                LOGGER.info("Unauthorized, trying to login again");
                this.login();
                return createUnit(unit);
            }
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

    public Unit updateUnit(Unit unit) {
        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.warn("Failed to parse media type");
        }

        JSONObject json = new JSONObject();
        if (unit.getPosition() != null) {
            json.put("latitude", unit.getPosition().getLatitude());
            json.put("longitude", unit.getPosition().getLongitude());
        }
        if (unit.getStatus() >= 0 && unit.getStatus() < 10) {
            json.put("unit_status", unit.getStatus());
        }
        RequestBody body = RequestBody.create(json.toJSONString(), mediaType);

        Request request = new Request.Builder()
                .url(apiUrl + "/units/" + unit.getId().toString() + "/")
                .patch(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();

            if (code == 401) {
                this.login();
                return updateUnit(unit);
            }
            if (code < 200 || code >= 300) {
                throw new RuntimeException("Failed to create unit; Code: " + code);
            }
            return Unit.of(response.body().string());
        } catch (IOException e) {
            LOGGER.error("Connection failed", e);
            throw new RuntimeException(e);
        }
    }
}
