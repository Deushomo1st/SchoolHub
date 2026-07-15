package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Per-session attendance - coexists with the old per-day `attendance` table. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "session_attendance")
public class SessionAttendance {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private String status = "present";

    @Column(name = "marked_by")
    private Long markedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getMarkedBy() { return markedBy; }
    public void setMarkedBy(Long markedBy) { this.markedBy = markedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
