package com.hykon.iot_backend.dto;

import com.hykon.iot_backend.entity.User;

public class LoginResponse {

    private Long id;
    private String name;
    private String email;
    private String role;
    private String status;

    public LoginResponse() {
    }

    public LoginResponse(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.role = user.getRole().getName().name();
        this.status = user.getStatus();
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
}