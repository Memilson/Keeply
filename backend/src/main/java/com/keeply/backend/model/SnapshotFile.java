/* Representa os metadados de um arquivo individual capturado em um snapshot de backup. */
package com.keeply.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "snapshot_files")
public class SnapshotFile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "snapshot_id", nullable = false)
    public UUID snapshotId;

    @Column(name = "relative_path", nullable = false, columnDefinition = "TEXT")
    public String relativePath;

    public long size;

    @Column(nullable = false, length = 64)
    public String sha256;

    @Column(name = "last_modified")
    public Instant lastModified;

    @Column(name = "created_at")
    public Instant createdAt;

    public SnapshotFile() {}

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
