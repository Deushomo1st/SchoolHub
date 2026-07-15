package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "attendance")
public class Attendance {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "class_id")
    private Long classId;

    @Column(name = "on_date", nullable = false)
    private LocalDate onDate;

    @Column(nullable = false)
    private String status = "present";

    @PrePersist void pre() {
        if (onDate == null) onDate = LocalDate.now();
        if (status == null) status = "present";
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public LocalDate getOnDate() { return onDate; }
    public void setOnDate(LocalDate onDate) { this.onDate = onDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
