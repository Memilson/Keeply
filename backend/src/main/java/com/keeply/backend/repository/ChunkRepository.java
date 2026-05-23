/*
 * Repositório para a entidade ChunkEntity.
 * Fornece métodos de acesso a dados para buscar e gerenciar os chunks (pedaços) de dados,
 * incluindo a busca por hash e identificação de chunks órfãos.
 */
package com.keeply.backend.repository;

import com.keeply.backend.model.ChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChunkRepository extends JpaRepository<ChunkEntity, UUID> {
    Optional<ChunkEntity> findByUserIdAndHash(UUID userId, String hash);
    List<ChunkEntity> findByUserIdAndHashIn(UUID userId, Collection<String> hashes);

    @Query("select c from ChunkEntity c where c.userId = :userId and c.id not in (select fc.chunkId from FileChunk fc)")
    List<ChunkEntity> findOrphanChunks(@Param("userId") UUID userId);
}
