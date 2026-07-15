package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Logic for advancing. Fixed rule-type menu (ATTENDANCE_MINIMUM, PREREQUISITE_COMPLETION,
 *  MANUAL_SCORE) computed entirely from Enrollment/offering_prerequisite/session_attendance -
 *  not a custom-formula parser. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "progression_rule")
public class ProgressionRule {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @Column(name = "scope_type", nullable = false)
    private String scopeType = "COHORT";

    @Column(name = "scope_ref_id", nullable = false)
    private Long scopeRefId;

    @Column(name = "threshold_value")
    private BigDecimal thresholdValue;

    @Column(name = "auto_issue_credential")
    private String autoIssueCredential;

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public Long getScopeRefId() { return scopeRefId; }
    public void setScopeRefId(Long scopeRefId) { this.scopeRefId = scopeRefId; }
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(BigDecimal thresholdValue) { this.thresholdValue = thresholdValue; }
    public String getAutoIssueCredential() { return autoIssueCredential; }
    public void setAutoIssueCredential(String autoIssueCredential) { this.autoIssueCredential = autoIssueCredential; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
