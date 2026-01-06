package dev.nilswitt.rk.edpmonitoring.enitites;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.util.UUID;

public class Unit {

    private static final Logger LOGGER = LogManager.getLogger(Unit.class);

    private UUID id;
    private String name;
    private int status = -1;
    private LngLat position = null;

    public Unit(UUID id, String name) {
        this.id = id;
        this.name = name;
    }


    public static Unit of(String json) {
        JSONParser parser = new JSONParser();
        try {
            JSONObject jsonObject = (JSONObject) parser.parse(json);
            UUID id = UUID.fromString((String) jsonObject.get("id"));
            String name = (String) jsonObject.get("name");

            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Unit name is missing in JSON");
            }
            Unit unit = new Unit(id, name.trim());
            Long status = (Long) jsonObject.get("unit_status");
            if (status != null) {
                unit.setStatus(status.intValue());
            }
            Double lat = (Double) jsonObject.get("latitude");
            Double lng = (Double) jsonObject.get("longitude");
            if (lat != null && lng != null) {
                unit.setPosition(new LngLat(lng, lat));
            }

            return unit;
        } catch (Exception e) {
            LOGGER.error("of()", e.getMessage());
            throw new RuntimeException(e);
        }

    }

    @Override
    public String toString() {
        return "Unit{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", position=" + position +
                '}';
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public LngLat getPosition() {
        return position;
    }

    public void setPosition(LngLat position) {
        this.position = position;
    }


}
