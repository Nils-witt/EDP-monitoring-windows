package dev.nilswitt.rk.edpmonitoring.enitites;

public class Position {

    private double longitude;
    private double latitude;

    public Position(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }
    public Position() {

    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    @Override
    public String toString() {
        return "Position{" +
                "longitude=" + longitude +
                ", latitude=" + latitude +
                '}';
    }
}
