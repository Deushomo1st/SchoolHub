package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "fee_invoice")
public class FeeInvoice {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String term = "Term 1";

    /** fee | book | participation | other - turns the invoice into a general payment obligation. */
    @Column(nullable = false)
    private String category = "fee";

    @Column(nullable = false)
    private boolean compulsory = true;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(columnDefinition = "text")
    private String description;

    /** Groups the per-student rows fanned out from one school-posted resource item. */
    @Column(name = "batch_id")
    private String batchId;

    @Column(name = "amount_naira", nullable = false)
    private Integer amountNaira;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private String status = "unpaid";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void pre() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (term == null) term = "Term 1";
        if (status == null) status = "unpaid";
        if (category == null) category = "fee";
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public boolean isCompulsory() { return compulsory; }
    public void setCompulsory(boolean compulsory) { this.compulsory = compulsory; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public Integer getAmountNaira() { return amountNaira; }
    public void setAmountNaira(Integer amountNaira) { this.amountNaira = amountNaira; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
