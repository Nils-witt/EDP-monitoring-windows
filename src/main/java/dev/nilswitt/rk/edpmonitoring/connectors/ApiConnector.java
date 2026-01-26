package dev.nilswitt.rk.edpmonitoring.connectors;

import dev.nilswitt.rk.edpmonitoring.connectors.apiRecords.ApiResponse;
import dev.nilswitt.rk.edpmonitoring.connectors.apiRecords.LoginPayload;
import dev.nilswitt.rk.edpmonitoring.connectors.apiRecords.LoginResponse;
import dev.nilswitt.rk.edpmonitoring.enitites.Position;
import dev.nilswitt.rk.edpmonitoring.enitites.Unit;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class ApiConnector {

    private String apiKey;
    private final String apiUrl;
    private final String username;
    private final String password;
    private static final Logger LOGGER = LogManager.getLogger(ApiConnector.class);
    private final ConfigConnector configConnector;
    private final ObjectMapper mapper = new ObjectMapper();

    public ApiConnector(String apiUrl, String apiKey, String username, String password, ConfigConnector configConnector) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.username = username;
        this.password = password;

        this.configConnector = configConnector;
    }
    public ApiConnector(ConfigConnector configConnector) {
        this.configConnector = configConnector;
        String apiUrl = configConnector.getConfigValue("api.url", "API_URL", "http://localhost:8080/api");
        String apiToken = configConnector.getConfigValue("api.token", "API_TOKEN", null);
        String apiUsername = configConnector.getConfigValue("api.username", "API_TOKEN", null);
        String apiPassword = configConnector.getConfigValue("api.password", "API_TOKEN", null);
        this.apiUrl = apiUrl;
        this.apiKey = apiToken;
        this.username = apiUsername;
        this.password = apiPassword;
        LOGGER.info("Using API URL: {}", apiUrl);
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

        LOGGER.info("testConnection: verifying token at {}", apiUrl + "/token/");
        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.warn("testConnection: Failed to parse media type");
            return false;
        }

        Request request = new Request.Builder()
                .url(apiUrl + "/token")
                .get()
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + this.apiKey)
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


        LoginPayload payload = new LoginPayload(this.username, this.password);
        RequestBody body = RequestBody.create(mapper.writeValueAsString(payload), mediaType);

        Request request = new Request.Builder()
                .url(this.apiUrl + "/token")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            LOGGER.info("login: received response code {}", code);
            if (code >= 200 && code < 300) {
                LoginResponse res = mapper.readValue(response.body().string(), LoginResponse.class);

                this.apiKey = res.token();
                LOGGER.info("login: authentication successful for user '{}'", this.username);
                return res.token();
            }
            LOGGER.warn("login: authentication failed; Code: {}", code);
            return null;
        } catch (IOException e) {
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

        RequestBody body = RequestBody.create("json.toJSONString()", mediaType);

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

        LOGGER.info("getAllUnits: requesting units from {}", apiUrl + "/units");
        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.warn("getAllUnits: Failed to parse media type");
        }

        Request request = new Request.Builder()
                .url(apiUrl + "/units")
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
            ObjectMapper mapper = new ObjectMapper();
            LOGGER.info("getAllUnits: parsing response {}", responseBody);
            ApiResponse objResponse = mapper.readValue(responseBody, ApiResponse.class);
            if (objResponse != null && objResponse._embedded != null && objResponse._embedded.unitList() != null) {
                units.addAll(Arrays.asList(objResponse._embedded.unitList()));
            }

            LOGGER.info("getAllUnits: response code {}, body: {}", code, objResponse.toString());

        } catch (IOException e) {
            LOGGER.error("getAllUnits: Connection failed", e);
        }

        return units;
    }


    public String getApiUrl() {
        return apiUrl;
    }

    record OutboxPayload(String OLD_STATUS, String NEW_STATUS, double OLD_KOORDX, double NEW_KOORDX, double OLD_KOORDY, double NEW_KOORDY) {
    }
    public void processOutboxRow(MariaDBConnector.WorkerOutbox row) {
        String payload = row.payload;
        String unitPK = row.pk;

        UUID unitID = configConnector.getUnitMappings().get(unitPK);
        Unit unit = configConnector.getUnits().get(unitID);
        if (unit == null) {
            //TODO: handle missing unit with creation
            LOGGER.error("processOutboxRow: No unit found for pk={}", unitPK);
            return;
        }



        try {
            OutboxPayload outboxPayload = mapper.readValue(payload, OutboxPayload.class);
            unit.setStatus(Integer.parseInt(outboxPayload.NEW_STATUS));
            unit.setPosition(new Position(outboxPayload.NEW_KOORDX, outboxPayload.NEW_KOORDY));

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
        LOGGER.info("createUnit: unit data: {}", mapper.writeValueAsString(unit));

        RequestBody body = RequestBody.create(mapper.writeValueAsString(unit), mediaType);

        Request request = new Request.Builder()
                .url(apiUrl + "/units")
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
            return mapper.readValue(response.body().string(), Unit.class);
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

        RequestBody body = RequestBody.create(this.mapper.writeValueAsString(unit), mediaType);

        Request request = new Request.Builder()
                .url(apiUrl + "/units/" + unit.getId().toString())
                .put(body)
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
                LOGGER.info(response.body().string());

                throw new RuntimeException("Failed to create unit; Code: " + code);
            }
            LOGGER.info("updateUnit: unit {} updated successfully", unit.getId());
            return this.mapper.readValue(response.body().string(), Unit.class);
        } catch (IOException e) {
            LOGGER.error("updateUnit: IOException while updating unit {}", unit.getId(), e);
            throw new RuntimeException(e);
        }
    }


}
