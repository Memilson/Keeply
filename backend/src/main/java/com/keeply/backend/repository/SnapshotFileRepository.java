/*
 * Repositório para a entidade SnapshotFile.
 * Permite buscar e manipular arquivos associados a um snapshot específico.
 */
package com.keeply.backend.repository;

import com.keeply.backend.model.SnapshotFile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SnapshotFileRepository extends JpaRepository<SnapshotFile, UUID> {
    List<SnapshotFile> findBySnapshotId(UUID snapshotId);
}
