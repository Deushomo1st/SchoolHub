package com.schoolhub.tenantservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public school self-registration payload. */
public class SignupRequest {

    @NotBlank @Size(max = 128)
    private String schoolName;

    /** Becomes the school's URL slug AND its Postgres schema name. Validated server-side. */
    @NotBlank
    private String code;

    // Optional: schools shape their own structure with the in-app designer.
    // Defaults to "generic" server-side when absent. Kept for backward compatibility.
    private String templateKey;     // nigerian_secondary | primary | university | generic

    @NotBlank
    private String planName;        // Free | Standard | Premium

    @NotBlank @Email
    private String adminEmail;
    @NotBlank @Size(min = 8, message = "Admin password must be at least 8 characters")
    private String adminPassword;
    @NotBlank
    private String adminFirstName;
    @NotBlank
    private String adminLastName;
    private String adminPhone;

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
    public String getAdminFirstName() { return adminFirstName; }
    public void setAdminFirstName(String adminFirstName) { this.adminFirstName = adminFirstName; }
    public String getAdminLastName() { return adminLastName; }
    public void setAdminLastName(String adminLastName) { this.adminLastName = adminLastName; }
    public String getAdminPhone() { return adminPhone; }
    public void setAdminPhone(String adminPhone) { this.adminPhone = adminPhone; }
}
