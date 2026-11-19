package model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public abstract class Entity implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Entity() {
        this(null);
    }

    protected Entity(String id) {
        this.id = (id == null || id.isBlank())
                ? UUID.randomUUID().toString()
                : id.trim();

        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public synchronized LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    protected synchronized void markUpdated() {
        updatedAt = LocalDateTime.now();
    }
}
