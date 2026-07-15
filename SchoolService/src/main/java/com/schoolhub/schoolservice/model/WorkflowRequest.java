package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * The one generalized propose/confirm/protest entity - one row per proposed action, replacing
 * what would otherwise be a bespoke table per feature. Generalizes FeeInvoice.batchId + status +
 * audit-on-transition (FeeService.postResource/approveBatch/rejectBatch) into a reusable shape.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "workflow_request", schema = "platform")
public class WorkflowRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "workflow_type", nullable = false)
    private String workflowType;

    private String payload;

    @Column(name = "protest_style", nullable = false)
    private String protestStyle = "NONE";

    @Column(nullable = false)
    private String state = "pending_confirmation";

    @Column(name = "protest_deadline")
    private LocalDateTime protestDeadline;

    /** Escalates (1->2) on a non-Moderator's second instead of the protest vetoing outright;
     *  only a Moderator's decision at tier>=2 is final. See WorkflowService.secondProtest(). */
    @Column(name = "approver_tier", nullable = false)
    private Integer approverTier = 1;

    @Column(name = "initiated_by", nullable = false)
    private Long initiatedBy;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getWorkflowType() { return workflowType; }
    public void setWorkflowType(String workflowType) { this.workflowType = workflowType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getProtestStyle() { return protestStyle; }
    public void setProtestStyle(String protestStyle) { this.protestStyle = protestStyle; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public LocalDateTime getProtestDeadline() { return protestDeadline; }
    public void setProtestDeadline(LocalDateTime protestDeadline) { this.protestDeadline = protestDeadline; }
    public Integer getApproverTier() { return approverTier; }
    public void setApproverTier(Integer approverTier) { this.approverTier = approverTier; }
    public Long getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(Long initiatedBy) { this.initiatedBy = initiatedBy; }
    public Long getDecidedBy() { return decidedBy; }
    public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
