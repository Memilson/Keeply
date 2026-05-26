package com.keeply.backend.repository;

import com.keeply.backend.model.TransferSession;
import com.keeply.backend.model.TransferSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferSessionRepository extends JpaRepository<TransferSession, UUID> {
    Optional<TransferSession> findByIdAndUserIdAndDeviceId(UUID id, UUID userId, UUID deviceId);
    List<TransferSession> findByStatusAndExpiresAtBefore(TransferSessionStatus status, Instant expiresAt);
}
