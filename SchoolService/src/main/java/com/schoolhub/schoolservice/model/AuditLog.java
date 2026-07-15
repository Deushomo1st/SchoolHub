package com.schoolhub.schoolservice.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Append-only audit row in the shared platform schema (who did what). */
@Entity
@Table(name = "audit_log", schema = "platform")
public class AuditLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false)
    private String action;

    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public void setAction(String action) { this.action = action; }
    public void setDetail(String detail) { this.detail = detail; }
}
