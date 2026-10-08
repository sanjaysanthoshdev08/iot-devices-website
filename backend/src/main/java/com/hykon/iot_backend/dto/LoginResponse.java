package com.hykon.iot_backend.dto;

import com.hykon.iot_backend.entity.User;

public class LoginResponse {

    private Long id;
    private String name;
    private String email;
    private String role;
    private String status;
    private String accessToken;
    private String refreshToken;
    private String tokenType;

    public LoginResponse() {
    }

    public LoginResponse(
            User user,
            String accessToken,
            String refreshToken
    ) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.role = user.getRole().getName().name();
        this.status = user.getStatus();
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
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