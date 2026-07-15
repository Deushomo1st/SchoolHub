package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

/** Configurable per-Offering prerequisite: can't take offeringId until prerequisiteId is done. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "offering_prerequisite")
public class OfferingPrerequisite {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offering_id", nullable = false)
    private Long offeringId;

    @Column(name = "prerequisite_id", nullable = false)
    private Long prerequisiteId;

    public Long getId() { return id; }
    public Long getOfferingId() { return offeringId; }
    public void setOfferingId(Long offeringId) { this.offeringId = offeringId; }
    public Long getPrerequisiteId() { return prerequisiteId; }
    public void setPrerequisiteId(Long prerequisiteId) { this.prerequisiteId = prerequisiteId; }
}
