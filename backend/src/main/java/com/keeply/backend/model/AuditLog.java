/* Entidade que representa os logs de auditoria do sistema, registrando eventos de usuários e dispositivos. */
package com.keeply.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_audit_user"))
    public UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", foreignKey = @ForeignKey(name = "fk_audit_device"))
    public Device device;

    @Column(name = "event_type", nullable = false)
    public String eventType;

    @Column(columnDefinition = "TEXT")
    public String message;

    @Column(columnDefinition = "TEXT")
    public String metadataJson;

    @Column(name = "created_at")
    public Instant createdAt;

    public AuditLog() {}

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
