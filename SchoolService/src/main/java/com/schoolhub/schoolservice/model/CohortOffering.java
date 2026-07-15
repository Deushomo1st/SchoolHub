package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

/** Who teaches which Offering in which Cohort - same shape as class_subject, new-model equivalent. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "cohort_offering")
public class CohortOffering {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(name = "offering_id", nullable = false)
    private Long offeringId;

    @Column(name = "teacher_id")
    private Long teacherId;

    public Long getId() { return id; }
    public Long getCohortId() { return cohortId; }
    public void setCohortId(Long cohortId) { this.cohortId = cohortId; }
    public Long getOfferingId() { return offeringId; }
    public void setOfferingId(Long offeringId) { this.offeringId = offeringId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
}
