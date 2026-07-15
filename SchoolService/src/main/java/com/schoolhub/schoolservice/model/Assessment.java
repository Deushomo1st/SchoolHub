package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "assessment")
public class Assessment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Old model - nullable now that a new-model Assessment can target cohortOfferingId instead.
    @Column(name = "class_subject_id")
    private Long classSubjectId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String term = "Term 1";

    @Column(name = "max_score", nullable = false)
    private Integer maxScore = 100;

    @Column(name = "assessed_on", nullable = false)
    private LocalDate assessedOn;

    // New model
    @Column(name = "cohort_offering_id")
    private Long cohortOfferingId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "session_id")
    private Long sessionId;

    private java.math.BigDecimal weight;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "pass_mark_percent", nullable = false)
    private java.math.BigDecimal passMarkPercent = java.math.BigDecimal.valueOf(50);

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void pre() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (assessedOn == null) assessedOn = LocalDate.now();
        if (term == null) term = "Term 1";
        if (maxScore == null) maxScore = 100;
        if (passMarkPercent == null) passMarkPercent = java.math.BigDecimal.valueOf(50);
    }

    public Long getId() { return id; }
    public Long getClassSubjectId() { return classSubjectId; }
    public void setClassSubjectId(Long classSubjectId) { this.classSubjectId = classSubjectId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public Integer getMaxScore() { return maxScore; }
    public void setMaxScore(Integer maxScore) { this.maxScore = maxScore; }
    public LocalDate getAssessedOn() { return assessedOn; }
    public void setAssessedOn(LocalDate assessedOn) { this.assessedOn = assessedOn; }
    public Long getCohortOfferingId() { return cohortOfferingId; }
    public void setCohortOfferingId(Long cohortOfferingId) { this.cohortOfferingId = cohortOfferingId; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public java.math.BigDecimal getWeight() { return weight; }
    public void setWeight(java.math.BigDecimal weight) { this.weight = weight; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public java.math.BigDecimal getPassMarkPercent() { return passMarkPercent; }
    public void setPassMarkPercent(java.math.BigDecimal passMarkPercent) { this.passMarkPercent = passMarkPercent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
