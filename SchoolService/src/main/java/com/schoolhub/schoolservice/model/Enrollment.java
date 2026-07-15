package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** The hub: binds a Student to a Cohort and/or an Offering/Group for one schedule_period.
 *  Raw-FK-by-id, matching every other entity in this service (no JPA object graph anywhere
 *  here) - service code resolves the linked rows explicitly, same as PerspectiveService does. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "enrollment")
public class Enrollment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "cohort_id")
    private Long cohortId;

    @Column(name = "offering_id")
    private Long offeringId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "schedule_period_id", nullable = false)
    private Long schedulePeriodId;

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "enrolled_on", nullable = false)
    private LocalDate enrolledOn;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "closed_reason")
    private String closedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (enrolledOn == null) enrolledOn = LocalDate.now();
        if (status == null) status = "active";
    }
    @PreUpdate void preUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getCohortId() { return cohortId; }
    public void setCohortId(Long cohortId) { this.cohortId = cohortId; }
    public Long getOfferingId() { return offeringId; }
    public void setOfferingId(Long offeringId) { this.offeringId = offeringId; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getSchedulePeriodId() { return schedulePeriodId; }
    public void setSchedulePeriodId(Long schedulePeriodId) { this.schedulePeriodId = schedulePeriodId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getEnrolledOn() { return enrolledOn; }
    public void setEnrolledOn(LocalDate enrolledOn) { this.enrolledOn = enrolledOn; }
    public LocalDate getEndedOn() { return endedOn; }
    public void setEndedOn(LocalDate endedOn) { this.endedOn = endedOn; }
    public String getClosedReason() { return closedReason; }
    public void setClosedReason(String closedReason) { this.closedReason = closedReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
