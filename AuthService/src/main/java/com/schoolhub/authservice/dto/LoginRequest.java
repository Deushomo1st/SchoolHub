package com.schoolhub.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    // Accepts an email OR a username; no @Email so usernames pass validation.
    @NotBlank
    private String email;
    @NotBlank
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
