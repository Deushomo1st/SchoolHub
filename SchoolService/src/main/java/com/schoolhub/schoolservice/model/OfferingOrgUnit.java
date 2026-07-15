package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

/** Cross-listing: one Offering can belong to more than one Org Unit. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "offering_org_unit")
public class OfferingOrgUnit {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offering_id", nullable = false)
    private Long offeringId;

    @Column(name = "org_unit_id", nullable = false)
    private Long orgUnitId;

    public Long getId() { return id; }
    public Long getOfferingId() { return offeringId; }
    public void setOfferingId(Long offeringId) { this.offeringId = offeringId; }
    public Long getOrgUnitId() { return orgUnitId; }
    public void setOrgUnitId(Long orgUnitId) { this.orgUnitId = orgUnitId; }
}
