package com.keeply.backend.repository;

import com.keeply.backend.model.SnapshotFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SnapshotFileRepository extends JpaRepository<SnapshotFile, UUID> {
    Optional<SnapshotFile> findBySnapshotIdAndPath(UUID snapshotId, String path);
    Page<SnapshotFile> findBySnapshotId(UUID snapshotId, Pageable pageable);
    Page<SnapshotFile> findBySnapshotIdAndPathContainingIgnoreCase(UUID snapshotId, String path, Pageable pageable);
    Page<SnapshotFile> findBySnapshotIdAndPathStartingWith(UUID snapshotId, String prefix, Pageable pageable);
    void deleteBySnapshotId(UUID snapshotId);
}
