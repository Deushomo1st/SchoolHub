package com.schoolhub.tenantservice.dto;

public class SignupResponse {
    private Long tenantId;
    private String schoolName;
    private String code;
    private String schemaName;
    private String adminEmail;
    private String message;

    public SignupResponse(Long tenantId, String schoolName, String code, String schemaName, String adminEmail, String message) {
        this.tenantId = tenantId;
        this.schoolName = schoolName;
        this.code = code;
        this.schemaName = schemaName;
        this.adminEmail = adminEmail;
        this.message = message;
    }

    public Long getTenantId() { return tenantId; }
    public String getSchoolName() { return schoolName; }
    public String getCode() { return code; }
    public String getSchemaName() { return schemaName; }
    public String getAdminEmail() { return adminEmail; }
    public String getMessage() { return message; }
}
