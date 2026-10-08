package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.RegisterRequest;
import com.hykon.iot_backend.dto.RegisterResponse;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        User user = authService.register(request);

        RegisterResponse response = new RegisterResponse(user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}