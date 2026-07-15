package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Reduces outstanding the same way a payment does, without money changing hands. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "fee_waiver")
public class FeeWaiver {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "scholarship_rule_id")
    private Long scholarshipRuleId;

    @Column(name = "amount_naira", nullable = false)
    private Integer amountNaira;

    private String reason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getScholarshipRuleId() { return scholarshipRuleId; }
    public void setScholarshipRuleId(Long scholarshipRuleId) { this.scholarshipRuleId = scholarshipRuleId; }
    public Integer getAmountNaira() { return amountNaira; }
    public void setAmountNaira(Integer amountNaira) { this.amountNaira = amountNaira; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
