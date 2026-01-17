package dev.nilswitt.rk.edpmonitoring.enitites;

import java.util.UUID;

public abstract class AbstractEntity {

    private UUID id;

    public  AbstractEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
