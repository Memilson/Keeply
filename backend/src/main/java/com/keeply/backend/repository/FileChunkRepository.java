/*
 * Repositório para a entidade FileChunk.
 * Permite a persistência e consulta do relacionamento entre os arquivos do snapshot e seus chunks.
 */
package com.keeply.backend.repository;

import com.keeply.backend.model.FileChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface FileChunkRepository extends JpaRepository<FileChunk, UUID> {
}
