package com.keeply.backend.repository;

import com.keeply.backend.model.FileChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface FileChunkRepository extends JpaRepository<FileChunk, UUID> {
}
