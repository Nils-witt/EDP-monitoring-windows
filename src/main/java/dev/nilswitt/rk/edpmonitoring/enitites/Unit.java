package dev.nilswitt.rk.edpmonitoring.enitites;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

public class Unit extends AbstractEntity {

    private static final Logger LOGGER = LogManager.getLogger(Unit.class);

    private String name;

    private Position position;

    private int status = 6;

    private boolean speakRequest = false;

    public Unit(){
        super(UUID.randomUUID());
    }
    public Unit(UUID id, String name) {
        super(id);
        this.name = name;
    }

    @Override
    public String toString() {
        return "Unit{" +
                "name='" + name + '\'' +
                ", position=" + position +
                ", status=" + status +
                ", speakRequest=" + speakRequest +
                '}';
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isSpeakRequest() {
        return speakRequest;
    }

    public void setSpeakRequest(boolean speakRequest) {
        this.speakRequest = speakRequest;
    }
}
