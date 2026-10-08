package com.hykon.iot_backend.dto;

public class RefreshTokenResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;

    public RefreshTokenResponse() {
    }

    public RefreshTokenResponse(
            String accessToken,
            String refreshToken
    ) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }
}