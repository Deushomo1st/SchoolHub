package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A protest against a WorkflowRequest. Has no binding effect by itself - only takes effect
 *  once a moderator seconds it (cancels the action) or dismisses it (clears the way to proceed). */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "workflow_protest", schema = "platform")
public class WorkflowProtest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_request_id", nullable = false)
    private Long workflowRequestId;

    @Column(name = "raised_by_user_id", nullable = false)
    private Long raisedByUserId;

    private String comment;

    @Column(nullable = false)
    private String status = "open";

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getWorkflowRequestId() { return workflowRequestId; }
    public void setWorkflowRequestId(Long workflowRequestId) { this.workflowRequestId = workflowRequestId; }
    public Long getRaisedByUserId() { return raisedByUserId; }
    public void setRaisedByUserId(Long raisedByUserId) { this.raisedByUserId = raisedByUserId; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDecidedBy() { return decidedBy; }
    public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
