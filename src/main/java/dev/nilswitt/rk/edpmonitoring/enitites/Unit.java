package dev.nilswitt.rk.edpmonitoring.enitites;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.util.UUID;

public class Unit {

    private static Logger logger = LogManager.getLogger(Unit.class);

    private UUID id;
    private String name;

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
            return new Unit(id, name.trim());
        } catch (Exception e) {
            logger.error("of()", e.getMessage());
            throw new RuntimeException(e);
        }

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
}
