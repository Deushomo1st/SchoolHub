package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "result")
public class Result {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private Double score;

    @Column(name = "is_resit", nullable = false)
    private boolean resit;

    @Column(nullable = false)
    private java.math.BigDecimal penalty = java.math.BigDecimal.ZERO;

    public Long getId() { return id; }
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public boolean isResit() { return resit; }
    public void setResit(boolean resit) { this.resit = resit; }
    public java.math.BigDecimal getPenalty() { return penalty; }
    public void setPenalty(java.math.BigDecimal penalty) { this.penalty = penalty == null ? java.math.BigDecimal.ZERO : penalty; }
}
