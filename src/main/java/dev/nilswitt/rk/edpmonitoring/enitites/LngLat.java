package dev.nilswitt.rk.edpmonitoring.enitites;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LngLat {

    private static final Logger LOGGER = LogManager.getLogger(LngLat.class);
    private double longitude;
    private double latitude;

    public LngLat(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    @Override
    public String toString() {
        return "LngLat{" +
                "longitude=" + longitude +
                ", latitude=" + latitude +
                '}';
    }
}
