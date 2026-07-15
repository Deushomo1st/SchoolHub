package com.schoolhub.authservice.dto;

import jakarta.validation.constraints.NotNull;

public class SwitchContextRequest {
    @NotNull
    private Long roleAssignmentId;

    public Long getRoleAssignmentId() { return roleAssignmentId; }
    public void setRoleAssignmentId(Long roleAssignmentId) { this.roleAssignmentId = roleAssignmentId; }
}
