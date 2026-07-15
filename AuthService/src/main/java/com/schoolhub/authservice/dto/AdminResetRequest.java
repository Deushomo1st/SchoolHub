package com.schoolhub.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A school admin resets one of their users to a new temp password. */
public class AdminResetRequest {
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 8, message = "New password must be at least 8 characters")
    private String newPassword;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
