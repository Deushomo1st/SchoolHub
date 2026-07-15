package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Institution-wide (not Org-Unit-owned): rooms, virtual rooms, equipment. Equipment is a
 *  subtype classified as "Room inventory" via parentResourceId, not independently bookable. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "resource")
public class Resource {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String kind = "room";

    private Integer capacity;

    @Column(name = "staff_only", nullable = false)
    private boolean staffOnly;

    @Column(name = "parent_resource_id")
    private Long parentResourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public boolean isStaffOnly() { return staffOnly; }
    public void setStaffOnly(boolean staffOnly) { this.staffOnly = staffOnly; }
    public Long getParentResourceId() { return parentResourceId; }
    public void setParentResourceId(Long parentResourceId) { this.parentResourceId = parentResourceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
