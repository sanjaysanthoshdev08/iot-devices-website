package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.ForgotPasswordRequest;
import com.hykon.iot_backend.dto.LogoutResponse;
import com.hykon.iot_backend.dto.ResetPasswordRequest;
import com.hykon.iot_backend.service.PasswordResetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Password reset", description = "Forgot password and reset password")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<LogoutResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.ok(new LogoutResponse(
                "If an account exists for this email, a password reset code has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<LogoutResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(
                request.getEmail(), request.getCode(),
                request.getPassword(), request.getConfirmPassword());
        return ResponseEntity.ok(new LogoutResponse(
                "Your password has been reset. You can now log in."));
    }
}
