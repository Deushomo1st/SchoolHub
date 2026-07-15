package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "school_class")
public class SchoolClass {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "level_label")
    private String levelLabel;

    @Column(name = "class_teacher_id")
    private Long classTeacherId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLevelLabel() { return levelLabel; }
    public void setLevelLabel(String levelLabel) { this.levelLabel = levelLabel; }
    public Long getClassTeacherId() { return classTeacherId; }
    public void setClassTeacherId(Long classTeacherId) { this.classTeacherId = classTeacherId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
