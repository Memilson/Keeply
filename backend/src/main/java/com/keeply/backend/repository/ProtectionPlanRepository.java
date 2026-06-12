package com.keeply.backend.repository;

import com.keeply.backend.model.ProtectionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProtectionPlanRepository extends JpaRepository<ProtectionPlan, UUID> {
    Optional<ProtectionPlan> findByDeviceId(UUID deviceId);
    void deleteByDeviceId(UUID deviceId);
}
