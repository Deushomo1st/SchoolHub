package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "fee_payment")
public class FeePayment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "amount_naira", nullable = false)
    private Integer amountNaira;

    @Column(nullable = false)
    private String method = "cash";

    /** payment | refund - a refund is a negative amountNaira row of this kind. */
    @Column(nullable = false)
    private String kind = "payment";

    private String reference;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void pre() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (paidOn == null) paidOn = LocalDate.now();
        if (method == null) method = "cash";
        if (kind == null) kind = "payment";
    }

    public Long getId() { return id; }
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    public Integer getAmountNaira() { return amountNaira; }
    public void setAmountNaira(Integer amountNaira) { this.amountNaira = amountNaira; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Long getRecordedBy() { return recordedBy; }
    public void setRecordedBy(Long recordedBy) { this.recordedBy = recordedBy; }
    public LocalDate getPaidOn() { return paidOn; }
    public void setPaidOn(LocalDate paidOn) { this.paidOn = paidOn; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
