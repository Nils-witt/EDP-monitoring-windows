package dev.nilswitt.rk.edpmonitoring.enitites;

public class LngLat {
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
