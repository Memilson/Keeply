package com.keeply.backend.repository;

import com.keeply.backend.model.FileChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FileChunkRepository extends JpaRepository<FileChunk, UUID> {
    List<FileChunk> findBySnapshotFileIdOrderByChunkIndexAsc(UUID snapshotFileId);
    void deleteBySnapshotFileIdIn(List<UUID> snapshotFileIds);
    @Query("SELECT COALESCE(SUM(fc.compressedSize), 0) FROM FileChunk fc WHERE fc.snapshotFile.snapshot.id = :snapshotId")
    long sumCompressedSizeBySnapshotIdAgg(@Param("snapshotId") UUID snapshotId);
}
