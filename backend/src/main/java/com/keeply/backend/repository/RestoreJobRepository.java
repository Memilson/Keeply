package com.keeply.backend.repository;

import com.keeply.backend.model.RestoreJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RestoreJobRepository extends JpaRepository<RestoreJob, UUID> {
    List<RestoreJob> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
