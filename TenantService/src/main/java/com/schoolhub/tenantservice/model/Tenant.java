package com.schoolhub.tenantservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "tenant", schema = "platform")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "schema_name", nullable = false, unique = true)
    private String schemaName;

    @Column(name = "template_key", nullable = false)
    private String templateKey = "generic";

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    /** School-issued code that lets staff self-sign-up into this school (null until generated). */
    @Column(name = "staff_code", unique = true)
    private String staffCode;

    @Column(name = "teacher_code", unique = true)
    private String teacherCode;

    @Column(name = "bursar_code", unique = true)
    private String bursarCode;

    @Column(name = "librarian_code", unique = true)
    private String librarianCode;

    @Column(nullable = false)
    private String status = "active";

    /** Stripe Billing: the school as a paying customer of the platform. */
    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Column(name = "stripe_sub_status")
    private String stripeSubStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }
    public String getStripeSubStatus() { return stripeSubStatus; }
    public void setStripeSubStatus(String stripeSubStatus) { this.stripeSubStatus = stripeSubStatus; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getStaffCode() { return staffCode; }
    public void setStaffCode(String staffCode) { this.staffCode = staffCode; }
    public String getTeacherCode() { return teacherCode; }
    public void setTeacherCode(String teacherCode) { this.teacherCode = teacherCode; }
    public String getBursarCode() { return bursarCode; }
    public void setBursarCode(String bursarCode) { this.bursarCode = bursarCode; }
    public String getLibrarianCode() { return librarianCode; }
    public void setLibrarianCode(String librarianCode) { this.librarianCode = librarianCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
