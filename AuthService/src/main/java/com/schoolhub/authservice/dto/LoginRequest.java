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

    // True only when the login came through the padlock (backdoor) path on the login page.
    // PLATFORM_OWNER logins are refused without it.
    private boolean lock;
    public boolean isLock() { return lock; }
    public void setLock(boolean lock) { this.lock = lock; }
}
