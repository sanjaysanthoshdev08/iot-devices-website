package com.hykon.iot_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {

    @NotBlank(message = "Reset token is required")
    @Size(max = 200, message = "Reset token is invalid")
    private String token;

    // Same password rules as registration.
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S+$",
            message = "Password must contain an uppercase letter, a lowercase letter, a digit, and a special character, with no whitespace"
    )
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    public ResetPasswordRequest() {
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
