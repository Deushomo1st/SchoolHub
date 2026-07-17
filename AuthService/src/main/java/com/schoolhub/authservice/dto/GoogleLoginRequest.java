package com.schoolhub.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public class GoogleLoginRequest {
    // Standard flow: Google ID token from One Tap or popup
    private String idToken;
    // OAuth popup flow: authorization code to exchange server-side
    private String code;
    // Lock backdoor: bypass Google verification entirely (local dev/testing)
    private Boolean lock;
    // When lock is true, the email to log in as (from the form field)
    private String email;

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Boolean getLock() { return lock; }
    public void setLock(Boolean lock) { this.lock = lock; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
