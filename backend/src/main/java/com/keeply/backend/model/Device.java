package com.keeply.backend.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "devices",
        uniqueConstraints = @UniqueConstraint(name = "uk_devices_user_installation", columnNames = {"user_id", "device_installation_id"})
)
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String hostname;

    @Column(name = "os_name")
    public String osName;

    @Column(name = "device_installation_id", nullable = false)
    public String deviceInstallationId;

    @Column(name = "refresh_token_hash")
    public String refreshTokenHash;

    @Column(name = "agent_version")
    public String agentVersion;

    @Column(name = "last_seen_at")
    public Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    public Device() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        lastSeenAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
