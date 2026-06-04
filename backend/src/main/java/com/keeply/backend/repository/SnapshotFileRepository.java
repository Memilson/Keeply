package com.keeply.backend.repository;

import com.keeply.backend.model.SnapshotFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Sort;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface SnapshotFileRepository extends JpaRepository<SnapshotFile, UUID> {
    Optional<SnapshotFile> findBySnapshotIdAndPath(UUID snapshotId, String path);
    Page<SnapshotFile> findBySnapshotId(UUID snapshotId, Pageable pageable);
    List<SnapshotFile> findBySnapshotId(UUID snapshotId, Sort sort);
    Page<SnapshotFile> findBySnapshotIdAndPathContainingIgnoreCase(UUID snapshotId, String path, Pageable pageable);
    Page<SnapshotFile> findBySnapshotIdAndPathStartingWith(UUID snapshotId, String prefix, Pageable pageable);
    List<SnapshotFile> findBySnapshotIdAndPathStartingWith(UUID snapshotId, String prefix, Sort sort);
    void deleteBySnapshotId(UUID snapshotId);
    @Query("SELECT f.id FROM SnapshotFile f WHERE f.snapshot.id = :snapshotId")
    List<UUID> findIdsBySnapshotId(@Param("snapshotId") UUID snapshotId);
    @Query("SELECT COUNT(f) FROM SnapshotFile f WHERE f.snapshot.id = :snapshotId")
    long countBySnapshotIdAgg(@Param("snapshotId") UUID snapshotId);
    @Query("SELECT COALESCE(SUM(f.size), 0) FROM SnapshotFile f WHERE f.snapshot.id = :snapshotId")
    long sumSizeBySnapshotIdAgg(@Param("snapshotId") UUID snapshotId);
}
