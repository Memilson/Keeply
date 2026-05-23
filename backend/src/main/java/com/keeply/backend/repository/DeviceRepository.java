/*
 * Repositório para a entidade Device.
 * Fornece métodos para acessar os dados dos dispositivos registrados pelos usuários.
 */
package com.keeply.backend.repository;

import com.keeply.backend.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    List<Device> findByUserId(UUID userId);
    Optional<Device> findByIdAndUserId(UUID id, UUID userId);
    List<Device> findAllByUserIdAndHostnameOrderByLastSeenAtDescCreatedAtDesc(UUID userId, String hostname);
}
