/* Representa uma solicitação para restaurar dados de um snapshot específico para um dispositivo. */
package com.keeply.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restore_jobs")
public class RestoreJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "device_id", nullable = false)
    public UUID deviceId;

    @Column(name = "snapshot_id", nullable = false)
    public UUID snapshotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public RestoreStatus status;

    @Column(name = "started_at")
    public Instant startedAt;

    @Column(name = "completed_at")
    public Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    @Column(name = "created_at")
    public Instant createdAt;

    public RestoreJob() {}

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
