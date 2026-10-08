package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.LoginRequest;
import com.hykon.iot_backend.dto.LoginResponse;
import com.hykon.iot_backend.dto.RegisterRequest;
import com.hykon.iot_backend.dto.RegisterResponse;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.service.AuthService;
import com.hykon.iot_backend.service.LoginService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginService loginService;

    public AuthController(
            AuthService authService,
            LoginService loginService
    ) {
        this.authService = authService;
        this.loginService = loginService;
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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginService.LoginResult loginResult =
                loginService.login(request);

        LoginResponse response = new LoginResponse(
                loginResult.getUser(),
                loginResult.getAccessToken()
        );

        return ResponseEntity
                .ok(response);
    }
}