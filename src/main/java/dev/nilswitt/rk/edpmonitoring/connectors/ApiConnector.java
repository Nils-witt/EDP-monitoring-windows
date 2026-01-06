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

        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.warn("testConnection: apiUrl is null or empty");
            return false;
        }

        if (this.apiKey == null || this.apiKey.isEmpty()) {
            LOGGER.debug("testConnection: apiKey missing, attempting login");
            this.login();
        }
        if (this.apiKey == null || this.apiKey.isEmpty()) {
            LOGGER.warn("testConnection: apiKey is empty or null after login");
            return false;
        }

        LOGGER.info("testConnection: verifying token at {}", apiUrl + "/token/verify/");
        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.warn("testConnection: Failed to parse media type");
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
            LOGGER.info("testConnection: received response code {}", code);
            return code >= 200 && code < 300;
        } catch (IOException e) {
            LOGGER.error("testConnection: IOException while testing connection to {}", apiUrl, e);
            return false;
        }
    }

    public String login() {
        if (this.apiUrl == null || this.apiUrl.isEmpty()) {
            LOGGER.warn("login: apiUrl is null or empty");
            return null;
        }

        LOGGER.info("login: attempting to authenticate user '{}' at {}", this.username, this.apiUrl);
        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.error("login: Failed to parse media type");
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
            LOGGER.info("login: received response code {}", code);
            if (code >= 200 && code < 300) {
                String res = response.body().string();
                JSONObject resJson = (JSONObject) new JSONParser().parse(res);
                String token = (String) resJson.get("access");
                this.apiKey = token;
                LOGGER.info("login: authentication successful for user '{}'", this.username);
                return token;
            }
            LOGGER.warn("login: authentication failed; Code: {}", code);
            return null;
        } catch (IOException | ParseException e) {
            LOGGER.error("login: Exception during authentication for user '{}'", this.username, e);
            return null;
        }
    }


    private void setUnitStatus(String unitId, int status) {
        LOGGER.info("setUnitStatus: Setting status of unit {} to {}", unitId, status);
        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.warn("setUnitStatus: apiUrl is null or empty");
        }

        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.error("setUnitStatus: Failed to parse media type");
        }

        JSONObject json = new JSONObject();
        json.put("unit_status", status);
        RequestBody body = RequestBody.create(json.toJSONString(), mediaType);

        String endpoint = apiUrl + "/units/{}/".replace("{}", unitId);
        LOGGER.debug("setUnitStatus: PATCH {}", endpoint);
        Request request = new Request.Builder()
                .url(endpoint)
                .patch(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            LOGGER.info("setUnitStatus: response code {}", code);

            if (code == 401) {
                LOGGER.info("setUnitStatus: unauthorized, attempting re-login and retry");
                this.login();
                setUnitStatus(unitId, status);
                return;
            }
            if (code < 200 || code >= 300) {
                LOGGER.error("setUnitStatus: Failed to set status for unit {}; Code: {}", unitId, code);
            }
        } catch (IOException e) {
            LOGGER.error("setUnitStatus: IOException while updating unit {}", unitId, e);
        }
    }


    public ArrayList<Unit> getAllUnits() {
        ArrayList<Unit> units = new ArrayList<>();

        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.warn("getAllUnits: apiUrl is null or empty");
        }

        LOGGER.info("getAllUnits: requesting units from {}", apiUrl + "/units/");
        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.warn("getAllUnits: Failed to parse media type");
        }

        Request request = new Request.Builder()
                .url(apiUrl + "/units/")
                .get()
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            String responseBody = response.body().string();
            LOGGER.debug("getAllUnits: response code {}, body: {}", code, responseBody);
            if (code == 401) {
                LOGGER.info("getAllUnits: unauthorized, attempting re-login");
                this.login();
                return getAllUnits();
            }
            if (code < 200 || code >= 300) {
                LOGGER.error("getAllUnits: Failed to get units; Code: {}", code);
            }
            JSONArray jsonArray = (JSONArray) new JSONParser().parse(responseBody);
            for (Object o : jsonArray) {
                JSONObject unitJson = (JSONObject) o;
                Unit unit = Unit.of(unitJson.toJSONString());
                units.add(unit);
            }
        } catch (IOException e) {
            LOGGER.error("getAllUnits: Connection failed", e);
        } catch (ParseException e) {
            LOGGER.error("getAllUnits: Failed to parse units response", e);
            throw new RuntimeException(e);
        }

        return units;
    }


    public String getApiUrl() {
        return apiUrl;
    }

    public void processOutboxRow(MariaDBConnector.WorkerOutbox row) {
        String payload = row.payload;
        String unitPK = row.pk;
        JSONParser parser = new JSONParser();
        try {
            Unit unit = new Unit(UUID.fromString(getApiIdForUnitName(unitPK)), unitPK);
            JSONObject json = (JSONObject) parser.parse(payload);
            if (json.containsKey("NEW_STATUS") && json.containsKey("OLD_STATUS")) {
                if (!json.get("OLD_STATUS").toString().equalsIgnoreCase(json.get("NEW_STATUS").toString())) {
                    int newStatus = Integer.parseInt(json.get("NEW_STATUS").toString());
                    unit.setStatus(newStatus);
                }
            }
            if (json.containsKey("OLD_KOORDX") && json.containsKey("NEW_KOORDX") && json.containsKey("OLD_KOORDY") && json.containsKey("NEW_KOORDY")) {
                if (!json.get("OLD_KOORDX").toString().equalsIgnoreCase(json.get("NEW_KOORDX").toString()) ||
                        !json.get("OLD_KOORDY").toString().equalsIgnoreCase(json.get("NEW_KOORDY").toString())) {
                    double newLat = Double.parseDouble(json.get("NEW_KOORDY").toString());
                    double newLng = Double.parseDouble(json.get("NEW_KOORDX").toString());
                    unit.setPosition(new LngLat(newLng, newLat));
                }
            }

            updateUnit(unit);

        } catch (Exception e) {
            LOGGER.error("processOutboxRow: failed to process outbox row for pk={}", row.pk, e);
        }


    }

    public Unit createUnit(Unit unit) {
        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.warn("createUnit: apiUrl is null or empty");
        }

        LOGGER.info("createUnit: creating unit '{}'", unit.getName());
        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.error("createUnit: Failed to parse media type");
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
            LOGGER.info("createUnit: response code {}", code);

            if (code == 401) {
                LOGGER.info("createUnit: unauthorized, attempting re-login");
                this.login();
                return createUnit(unit);
            }
            if (code < 200 || code >= 300) {
                LOGGER.error("createUnit: Failed to create unit '{}'; Code: {}", unit.getName(), code);
                throw new RuntimeException("Failed to create unit; Code: " + code);
            }
            LOGGER.info("createUnit: unit '{}' created successfully", unit.getName());
            return Unit.of(response.body().string());
        } catch (IOException e) {
            LOGGER.error("createUnit: IOException while creating unit '{}'", unit.getName(), e);
            throw new RuntimeException(e);
        }
    }

    public Unit updateUnit(Unit unit) {
        LOGGER.info("updateUnit: updating unit id={}", unit.getId());
        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.error("updateUnit: Failed to parse media type");
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
            LOGGER.info("updateUnit: response code {}", code);

            if (code == 401) {
                LOGGER.info("updateUnit: unauthorized, attempting re-login");
                this.login();
                return updateUnit(unit);
            }
            if (code < 200 || code >= 300) {
                LOGGER.error("updateUnit: Failed to update unit {}; Code: {}", unit.getId(), code);
                throw new RuntimeException("Failed to create unit; Code: " + code);
            }
            LOGGER.info("updateUnit: unit {} updated successfully", unit.getId());
            return Unit.of(response.body().string());
        } catch (IOException e) {
            LOGGER.error("updateUnit: IOException while updating unit {}", unit.getId(), e);
            throw new RuntimeException(e);
        }
    }
}
