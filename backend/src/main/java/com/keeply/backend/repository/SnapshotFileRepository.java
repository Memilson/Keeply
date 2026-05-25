package com.keeply.backend.repository;

import com.keeply.backend.model.SnapshotFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SnapshotFileRepository extends JpaRepository<SnapshotFile, UUID> {
    List<SnapshotFile> findBySnapshotId(UUID snapshotId);
    void deleteBySnapshotId(UUID snapshotId);
}
