package com.schoolhub.authservice.dto;

public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserDto user;

    public TokenResponse(String accessToken, String refreshToken, long expiresIn, UserDto user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public long getExpiresIn() { return expiresIn; }
    public UserDto getUser() { return user; }
}
