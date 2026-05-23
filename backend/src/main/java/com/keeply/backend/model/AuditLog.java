package com.keeply.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "device_id")
    public UUID deviceId;

    @Column(name = "event_type", nullable = false)
    public String eventType;

    @Column(columnDefinition = "TEXT")
    public String message;

    @Column(columnDefinition = "TEXT")
    public String metadataJson;

    @Column(name = "created_at")
    public Instant createdAt;

    public AuditLog() {}

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
