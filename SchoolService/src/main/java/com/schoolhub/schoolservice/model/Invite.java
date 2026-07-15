package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Generalizes Tenant.staffCode's pattern (purpose-typed, expiring, multi-use, redeem-then-
 * confirm) into one mechanism: code is set for a code-based invite (CLASS_JOIN); target_user_id
 * is set instead for a directly-targeted request (GUARDIAN_LINK) whose recipient is already known.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "invite", schema = "platform")
public class Invite {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "issuer_user_id", nullable = false)
    private Long issuerUserId;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "scope_type")
    private String scopeType;

    @Column(name = "scope_ref_id")
    private Long scopeRefId;

    private String code;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "max_redemptions", nullable = false)
    private Integer maxRedemptions = 1;

    @Column(name = "redemption_count", nullable = false)
    private Integer redemptionCount = 0;

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getIssuerUserId() { return issuerUserId; }
    public void setIssuerUserId(Long issuerUserId) { this.issuerUserId = issuerUserId; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public Long getScopeRefId() { return scopeRefId; }
    public void setScopeRefId(Long scopeRefId) { this.scopeRefId = scopeRefId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public Integer getMaxRedemptions() { return maxRedemptions; }
    public void setMaxRedemptions(Integer maxRedemptions) { this.maxRedemptions = maxRedemptions; }
    public Integer getRedemptionCount() { return redemptionCount; }
    public void setRedemptionCount(Integer redemptionCount) { this.redemptionCount = redemptionCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
