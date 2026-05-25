package com.keeply.backend.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "file_chunks",
    uniqueConstraints = @UniqueConstraint(name = "uk_file_chunks_order", columnNames = {"snapshot_file_id", "chunk_index"}),
    indexes = {
        @Index(name = "idx_file_chunks_file", columnList = "snapshot_file_id"),
        @Index(name = "idx_file_chunks_hash", columnList = "chunk_hash")
    }
)
public class FileChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_file_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chunk_file"))
    public SnapshotFile snapshotFile;

    @Column(name = "chunk_index", nullable = false)
    public int chunkIndex;

    @Column(name = "chunk_hash", nullable = false, length = 64)
    public String chunkHash;

    // Tamanhos originais e comprimidos mantidos para facilitar listagem sem join pesado, 
    // mas agora vinculados via integridade referencial ao arquivo.
    
    @Column(name = "original_size", nullable = false)
    public long originalSize;

    @Column(name = "compressed_size", nullable = false)
    public long compressedSize;
}
