package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Nests inside a Cohort: an informal sub-team, or (when offeringId is set) the subset
 *  of the cohort taking that Offering. Table name is a reserved word, hence the backticks
 *  - Hibernate quotes it per-dialect (renders as "group" on Postgres). */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "`group`")
public class Group {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(name = "offering_id")
    private Long offeringId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getCohortId() { return cohortId; }
    public void setCohortId(Long cohortId) { this.cohortId = cohortId; }
    public Long getOfferingId() { return offeringId; }
    public void setOfferingId(Long offeringId) { this.offeringId = offeringId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
