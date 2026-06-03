package com.keeply.backend.model;

import jakarta.persistence.*;

import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_sessions", indexes = {
        @Index(name = "idx_transfer_session_expiry", columnList = "status, expires_at"),
        @Index(name = "idx_transfer_session_snapshot", columnList = "snapshot_id")
})
public class TransferSession implements Persistable<UUID> {
    @Id
    public UUID id;

    @Version
    @Column(nullable = false)
    public long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TransferSessionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TransferSessionStatus status;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "device_id", nullable = false)
    public UUID deviceId;

    @Column(name = "snapshot_id", nullable = false)
    public UUID snapshotId;

    @Column(name = "minio_access_key", length = 255)
    public String minioAccessKey;

    @Column(name = "expires_at")
    public Instant expiresAt;

    @Column(name = "last_renewed_at")
    public Instant lastRenewedAt;

    @Column(name = "staging_prefix", columnDefinition = "TEXT")
    public String stagingPrefix;

    @Column(name = "closed_reason", columnDefinition = "TEXT")
    public String closedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Transient
    private boolean isNew = true;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        isNew = false;
    }

    @PostLoad
    void onPostLoad() {
        isNew = false;
    }
}
