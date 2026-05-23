/* Representa um ponto no tempo de um backup (snapshot) dos dados de um dispositivo. */
package com.keeply.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "snapshots")
public class Snapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "device_id", nullable = false)
    public UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public SnapshotStatus status;

    @Column(name = "source_path", columnDefinition = "TEXT")
    public String sourcePath;

    @Column(name = "total_files")
    public long totalFiles;

    @Column(name = "total_original_size")
    public long totalOriginalSize;

    @Column(name = "total_compressed_size")
    public long totalCompressedSize;

    @Column(name = "manifest_key", columnDefinition = "TEXT")
    public String manifestKey;

    @Column(name = "started_at")
    public Instant startedAt;

    @Column(name = "completed_at")
    public Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public Snapshot() {}

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
