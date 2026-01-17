package dev.nilswitt.rk.edpmonitoring.connectors.apiRecords;

import dev.nilswitt.rk.edpmonitoring.enitites.Unit;

public class ApiResponse {
    public embeddedResponse _embedded;

    @Override
    public String toString() {
        return "ApiResponse{" +
                "_embedded=" + _embedded +
                '}';
    }

    public ApiResponse() {

    }

    public record embeddedResponse(Unit[] unitList) {

    }
}

