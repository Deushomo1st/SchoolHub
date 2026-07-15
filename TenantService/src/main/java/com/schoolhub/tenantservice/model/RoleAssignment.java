package com.schoolhub.tenantservice.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Source of truth for "who holds what role, where". A person can hold several
 * of these (teacher at one school, guardian at another) - app_user.role_id/
 * tenant_id stay as legacy single-role fields, read only as a fallback while
 * a user has no assignment rows yet. TenantService uses this for admin-side
 * assignment management (granting an existing user a second role/school).
 */
@Entity
@Table(name = "role_assignment", schema = "platform")
public class RoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_user_id", nullable = false)
    private Long appUserId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "scope_type")
    private String scopeType;

    @Column(name = "scope_ref_id")
    private Long scopeRefId;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "granted_by")
    private Long grantedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "active";
    }

    public Long getId() { return id; }
    public Long getAppUserId() { return appUserId; }
    public void setAppUserId(Long appUserId) { this.appUserId = appUserId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public Long getScopeRefId() { return scopeRefId; }
    public void setScopeRefId(Long scopeRefId) { this.scopeRefId = scopeRefId; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getGrantedBy() { return grantedBy; }
    public void setGrantedBy(Long grantedBy) { this.grantedBy = grantedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
