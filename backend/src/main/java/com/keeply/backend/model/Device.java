package com.keeply.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(nullable = false)
    public String name;

    public String hostname;
    public String os;

    @Column(name = "agent_version")
    public String agentVersion;

    @Column(name = "last_seen_at")
    public Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public Device() {}

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        lastSeenAt = createdAt;
    }
}
