package com.keeply.backend.repository;

import com.keeply.backend.model.FileChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FileChunkRepository extends JpaRepository<FileChunk, UUID> {
    List<FileChunk> findBySnapshotFileIdOrderByChunkIndexAsc(UUID snapshotFileId);
    void deleteBySnapshotFileIdIn(List<UUID> snapshotFileIds);
}
