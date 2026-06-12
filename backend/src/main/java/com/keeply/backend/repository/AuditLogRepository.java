/*
 * Repositório para a entidade AuditLog.
 * Fornece métodos de acesso a dados para operações de log de auditoria no banco de dados.
 */
package com.keeply.backend.repository;

import com.keeply.backend.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    void deleteByDeviceId(UUID deviceId);
}
