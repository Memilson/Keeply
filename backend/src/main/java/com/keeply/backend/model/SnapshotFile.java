package com.keeply.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "snapshot_files", 
    uniqueConstraints = @UniqueConstraint(name = "uk_snapshot_file_path", columnNames = {"snapshot_id", "path"}),
    indexes = {
        @Index(name = "idx_snapshot_files_snapshot", columnList = "snapshot_id"),
        @Index(name = "idx_snapshot_files_path", columnList = "path")
    }
)
public class SnapshotFile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_file_snapshot"))
    public Snapshot snapshot;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String path;

    @Column(nullable = false)
    public long size;

    @Column(name = "last_modified")
    public Instant lastModified;

    @Column(nullable = false, length = 64)
    public String sha256;

    @OneToMany(mappedBy = "snapshotFile", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<FileChunk> chunks;
}
