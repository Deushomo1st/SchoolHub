package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One row per (rule, enrollment) evaluation - keeps history and prevents blind re-evaluation. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "progression_result")
public class ProgressionResult {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(nullable = false)
    private boolean passed;

    private String detail;

    @Column(name = "evaluated_by")
    private Long evaluatedBy;

    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;

    @PrePersist void pre() { if (evaluatedAt == null) evaluatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(Long enrollmentId) { this.enrollmentId = enrollmentId; }
    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Long getEvaluatedBy() { return evaluatedBy; }
    public void setEvaluatedBy(Long evaluatedBy) { this.evaluatedBy = evaluatedBy; }
    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
