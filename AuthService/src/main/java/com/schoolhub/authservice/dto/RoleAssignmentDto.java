package com.schoolhub.authservice.dto;

public class RoleAssignmentDto {
    private Long id;
    private String roleName;
    private Long tenantId;
    private String tenantName;
    private boolean isDefault;

    public RoleAssignmentDto(Long id, String roleName, Long tenantId, String tenantName, boolean isDefault) {
        this.id = id;
        this.roleName = roleName;
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.isDefault = isDefault;
    }

    public Long getId() { return id; }
    public String getRoleName() { return roleName; }
    public Long getTenantId() { return tenantId; }
    public String getTenantName() { return tenantName; }
    public boolean isDefault() { return isDefault; }
}
