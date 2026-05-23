/* Entidade que representa um pedaço de dado (chunk) armazenado, utilizado para deduplicação e controle de tamanho. */
package com.keeply.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "chunks",
    uniqueConstraints = @UniqueConstraint(name = "uk_chunks_user_hash", columnNames = {"user_id", "hash"})
)
public class ChunkEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(nullable = false, length = 64)
    public String hash;

    @Column(name = "original_size")
    public long originalSize;

    @Column(name = "compressed_size")
    public long compressedSize;

    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    public String storageKey;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public ChunkEntity() {}

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
