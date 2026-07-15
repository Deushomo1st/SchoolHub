package com.schoolhub.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public class GoogleLoginRequest {
    @NotBlank
    private String idToken;

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }
}
