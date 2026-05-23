package com.keeply.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "file_chunks",
    uniqueConstraints = @UniqueConstraint(name = "uk_file_chunk_index", columnNames = {"snapshot_file_id", "chunk_index"})
)
public class FileChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "snapshot_file_id", nullable = false)
    public UUID snapshotFileId;

    @Column(name = "chunk_id", nullable = false)
    public UUID chunkId;

    @Column(name = "chunk_index", nullable = false)
    public int chunkIndex;

    @Column(name = "original_size")
    public long originalSize;

    @Column(name = "compressed_size")
    public long compressedSize;

    @Column(name = "created_at")
    public Instant createdAt;

    public FileChunk() {}

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
