package com.hykon.iot_backend.dto;

public class LogoutResponse {

    private String message;

    public LogoutResponse() {
    }

    public LogoutResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}