package com.schoolhub.tenantservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_event", schema = "platform")
public class ActivityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String action;

    @Column(name = "event_size")
    private double size;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void pre() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public ActivityEvent() {}
    public ActivityEvent(Long userId, String action, double size) {
        this.userId = userId;
        this.action = action;
        this.size = size;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getAction() { return action; }
    public double getSize() { return size; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setAction(String action) { this.action = action; }
    public void setSize(double size) { this.size = size; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
