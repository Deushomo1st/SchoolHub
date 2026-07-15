package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Fixed rule-type menu, same philosophy as ProgressionRule: TOP_PERFORMER (AcademicService
 *  transcript average), ATTENDANCE_THRESHOLD (session_attendance), MANUAL_FLAG (staff decision). */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "scholarship_rule")
public class ScholarshipRule {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @Column(name = "scope_type", nullable = false)
    private String scopeType = "OFFERING";

    @Column(name = "scope_ref_id")
    private Long scopeRefId;

    @Column(name = "threshold_value")
    private BigDecimal thresholdValue;

    @Column(name = "discount_percent", nullable = false)
    private BigDecimal discountPercent = BigDecimal.valueOf(100);

    private String category;

    @Column(nullable = false)
    private boolean active = true;

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
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
