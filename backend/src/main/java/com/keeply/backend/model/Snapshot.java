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

    // VULN-018: optimistic locking — previne lost updates em complete()/fail() concorrentes
    @Version
    public Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false, foreignKey = @ForeignKey(name = "fk_snapshot_device"))
    public Device device;

    // Removido o userId redundante, pois o snapshot já pertence a um device que pertence a um usuário
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public SnapshotStatus status;

    @Column(name = "source_path", columnDefinition = "TEXT")
    public String sourcePath;

    @Column(name = "total_files")
    public Long totalFiles;

    @Column(name = "total_original_size")
    public Long totalOriginalSize;

    @Column(name = "total_compressed_size")
    public Long totalCompressedSize;

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
