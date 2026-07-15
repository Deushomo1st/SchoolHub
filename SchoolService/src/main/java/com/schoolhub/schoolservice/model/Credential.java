package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Proof of completion an institution issues. Revocation escalates to Moderator (see
 *  WorkflowService) - Platform Owner-tier escalation doesn't fit the current per-tenant
 *  architecture without a cross-service or impersonation mechanism that doesn't exist yet. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "credential")
public class Credential {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_user_id", nullable = false)
    private Long personUserId;

    @Column(nullable = false)
    private String title;

    @Column(name = "criteria_ref")
    private String criteriaRef;

    @Column(name = "artifact_url")
    private String artifactUrl;

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "issued_by")
    private Long issuedBy;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @PrePersist void pre() { if (issuedAt == null) issuedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getPersonUserId() { return personUserId; }
    public void setPersonUserId(Long personUserId) { this.personUserId = personUserId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCriteriaRef() { return criteriaRef; }
    public void setCriteriaRef(String criteriaRef) { this.criteriaRef = criteriaRef; }
    public String getArtifactUrl() { return artifactUrl; }
    public void setArtifactUrl(String artifactUrl) { this.artifactUrl = artifactUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getIssuedBy() { return issuedBy; }
    public void setIssuedBy(Long issuedBy) { this.issuedBy = issuedBy; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public Long getRevokedBy() { return revokedBy; }
    public void setRevokedBy(Long revokedBy) { this.revokedBy = revokedBy; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
}
