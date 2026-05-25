package com.keeply.backend.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "protection_plans", uniqueConstraints = @UniqueConstraint(name = "uk_protection_plan_device", columnNames = "device_id"))
public class ProtectionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false, foreignKey = @ForeignKey(name = "fk_plan_device"))
    public Device device;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    public PlanType planType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "protection_plan_sources", joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "source_path", nullable = false)
    public List<String> sources = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
