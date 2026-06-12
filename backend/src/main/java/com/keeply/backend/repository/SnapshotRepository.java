/*
 * Repositório para a entidade Snapshot.
 * Fornece métodos para acessar e filtrar os snapshots de backup de usuários e dispositivos.
 */
package com.keeply.backend.repository;

import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SnapshotRepository extends JpaRepository<Snapshot, UUID> {
    @Query(
        value = "SELECT s FROM Snapshot s JOIN FETCH s.device WHERE s.device.user.id = :userId ORDER BY s.createdAt DESC",
        countQuery = "SELECT COUNT(s) FROM Snapshot s WHERE s.device.user.id = :userId"
    )
    Page<Snapshot> findByDeviceUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);
    List<Snapshot> findByDeviceUserIdAndDeviceIdAndStatusOrderByCreatedAtDesc(UUID userId, UUID deviceId, SnapshotStatus status);
    @Query("SELECT s FROM Snapshot s JOIN FETCH s.device WHERE s.id = :id AND s.device.user.id = :userId")
    Optional<Snapshot> findByIdAndDeviceUserId(@Param("id") UUID id, @Param("userId") UUID userId);
    boolean existsByDeviceIdAndStatusIn(UUID deviceId, java.util.Collection<SnapshotStatus> statuses);
    List<Snapshot> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId);
}
