package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Flat - a Cohort never nests inside another Cohort. Recreated every schedule_period;
 *  continuity across periods is tracked through Enrollment history, not this row persisting. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "cohort")
public class Cohort {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_period_id", nullable = false)
    private Long schedulePeriodId;

    @Column(nullable = false)
    private String name;

    @Column(name = "level_label")
    private String levelLabel;

    @Column(name = "head_user_id")
    private Long headUserId;

    private Integer capacity;

    @Column(name = "is_prime_level", nullable = false)
    private boolean primeLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getSchedulePeriodId() { return schedulePeriodId; }
    public void setSchedulePeriodId(Long schedulePeriodId) { this.schedulePeriodId = schedulePeriodId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLevelLabel() { return levelLabel; }
    public void setLevelLabel(String levelLabel) { this.levelLabel = levelLabel; }
    public Long getHeadUserId() { return headUserId; }
    public void setHeadUserId(Long headUserId) { this.headUserId = headUserId; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public boolean isPrimeLevel() { return primeLevel; }
    public void setPrimeLevel(boolean primeLevel) { this.primeLevel = primeLevel; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
