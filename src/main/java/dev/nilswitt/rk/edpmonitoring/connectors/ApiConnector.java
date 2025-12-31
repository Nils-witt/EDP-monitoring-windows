package dev.nilswitt.rk.edpmonitoring.connectors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ApiConnector {

    private final String apiKey;
    private final String apiUrl;
    private static final Logger LOGGER = Logger.getLogger(ApiConnector.class.getName());

    public ApiConnector(String apiUrl, String apiKey) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
    }

    public void connect() {
        // Implement connection logic here
        System.out.println("Connecting to API at " + apiUrl + " with key " + apiKey);
    }

    public boolean testConnection() {
        LOGGER.info("Connecting to API at " + apiUrl + " with key " + apiKey.substring(0,5) + "*****");

        if (apiUrl == null || apiUrl.isEmpty()) {
            LOGGER.log(Level.WARNING, "apiUrl is empty or null");
            return false;
        }

        OkHttpClient client = new OkHttpClient.Builder().build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        if (mediaType == null) {
            LOGGER.log(Level.WARNING, "Failed to parse media type");
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
            LOGGER.log(Level.SEVERE, "Connection test failed", e);
            return false;
        }
    }


    public String getApiUrl() {
        return apiUrl;
    }
}
