/*
 * Repositório para a entidade Snapshot.
 * Fornece métodos para acessar e filtrar os snapshots de backup de usuários e dispositivos.
 */
package com.keeply.backend.repository;

import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SnapshotRepository extends JpaRepository<Snapshot, UUID> {
    List<Snapshot> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Snapshot> findByUserIdAndDeviceIdAndStatusOrderByCreatedAtDesc(UUID userId, UUID deviceId, SnapshotStatus status);
    Optional<Snapshot> findByIdAndUserId(UUID id, UUID userId);
}
