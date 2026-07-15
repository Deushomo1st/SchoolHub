package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Self-referential: Program -> Course -> Module, no fixed level count - the same recursion
 *  trick as OrgUnit, applied to "what's taught" instead of "who's structured under whom". */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "offering")
public class Offering {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false)
    private String title;

    @Column(name = "credit_weight")
    private java.math.BigDecimal creditWeight;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public java.math.BigDecimal getCreditWeight() { return creditWeight; }
    public void setCreditWeight(java.math.BigDecimal creditWeight) { this.creditWeight = creditWeight; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
