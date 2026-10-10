package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.LoginRequest;
import com.hykon.iot_backend.dto.LoginResponse;
import com.hykon.iot_backend.dto.LogoutResponse;
import com.hykon.iot_backend.dto.RefreshTokenRequest;
import com.hykon.iot_backend.dto.RefreshTokenResponse;
import com.hykon.iot_backend.dto.RegisterRequest;
import com.hykon.iot_backend.dto.RegisterResponse;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.AuthService;
import com.hykon.iot_backend.service.LoginService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(
        name = "Authentication",
        description = "User registration, login, token refresh, and logout"
)
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

    @Operation(
            summary = "Register a new user",
            description = "Creates a user account and returns the registered user's details.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RegisterRequest.class),
                            examples = @ExampleObject(
                                    name = "Registration request",
                                    value = """
                                            {
                                              "name": "Example User",
                                              "email": "user@example.com",
                                              "password": "ExamplePass123!",
                                              "confirmPassword": "ExamplePass123!"
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Registration successful",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Registration response",
                                    value = """
                                            {
                                              "success": true,
                                              "message": "Registration successful",
                                              "data": {
                                                "id": 1,
                                                "name": "Example User",
                                                "email": "user@example.com",
                                                "role": "USER",
                                                "status": "ACTIVE",
                                                "createdAt": "2026-01-15T10:30:00Z"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "Validation error",
                                    value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Validation failed",
                                              "message": "Please correct the invalid request fields.",
                                              "errors": {
                                                "email": "Enter a valid email address",
                                                "password": "Password does not meet the requirements"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "An account with the supplied details already exists",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "Conflict error",
                                    value = """
                                            {
                                              "success": false,
                                              "status": 409,
                                              "error": "Conflict",
                                              "message": "The requested resource already exists."
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @org.springframework.web.bind.annotation.RequestBody
            RegisterRequest request
    ) {
        User user = authService.register(request);
        RegisterResponse response = new RegisterResponse(user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }

    @Operation(
            summary = "Log in",
            description = "Authenticates a user and returns user details, an access token, and a refresh token.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LoginRequest.class),
                            examples = @ExampleObject(
                                    name = "Login request",
                                    value = """
                                            {
                                              "email": "user@example.com",
                                              "password": "ExamplePass123!"
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Login successful",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    name = "Login response",
                                    value = """
                                            {
                                              "success": true,
                                              "message": "Login successful",
                                              "data": {
                                                "id": 1,
                                                "name": "Example User",
                                                "email": "user@example.com",
                                                "role": "USER",
                                                "status": "ACTIVE",
                                                "accessToken": "<access-token>",
                                                "refreshToken": "<refresh-token>",
                                                "tokenType": "Bearer"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Login request validation failed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "Validation error",
                                    value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Validation failed",
                                              "message": "Please correct the invalid request fields.",
                                              "errors": {
                                                "email": "Enter a valid email address"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Email or password is incorrect",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "Invalid credentials",
                                    value = """
                                            {
                                              "success": false,
                                              "status": 401,
                                              "error": "Unauthorized",
                                              "message": "Invalid email or password."
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @org.springframework.web.bind.annotation.RequestBody
            LoginRequest request
    ) {
        LoginService.LoginResult loginResult = loginService.login(request);

        LoginResponse response = new LoginResponse(
                loginResult.getUser(),
                loginResult.getAccessToken(),
                loginResult.getRefreshToken()
        );

        return ResponseEntity
                .ok(ApiResponse.success("Login successful", response));
    }

    @Operation(
            summary = "Refresh authentication tokens",
            description = "Uses a refresh token to obtain a new access token and refresh token.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RefreshTokenRequest.class),
                            examples = @ExampleObject(
                                    name = "Refresh token request",
                                    value = """
                                            {
                                              "refreshToken": "<refresh-token>"
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Tokens refreshed successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    name = "Refresh token response",
                                    value = """
                                            {
                                              "success": true,
                                              "message": "Token refreshed successfully",
                                              "data": {
                                                "accessToken": "<new-access-token>",
                                                "refreshToken": "<new-refresh-token>",
                                                "tokenType": "Bearer"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Refresh request validation failed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "Validation error",
                                    value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Validation failed",
                                              "message": "Please correct the invalid request fields.",
                                              "errors": {
                                                "refreshToken": "Refresh token is required"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Refresh token is invalid, expired, or revoked",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "Invalid refresh token",
                                    value = """
                                            {
                                              "success": false,
                                              "status": 401,
                                              "error": "Unauthorized",
                                              "message": "The token is invalid, expired, or revoked."
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refresh(
            @Valid @org.springframework.web.bind.annotation.RequestBody
            RefreshTokenRequest request
    ) {
        LoginService.RefreshResult refreshResult =
                loginService.refresh(request.getRefreshToken());

        RefreshTokenResponse response = new RefreshTokenResponse(
                refreshResult.getAccessToken(),
                refreshResult.getRefreshToken()
        );

        return ResponseEntity
                .ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @Operation(
            summary = "Log out",
            description = "Invalidates the refresh token supplied in the request.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RefreshTokenRequest.class),
                            examples = @ExampleObject(
                                    name = "Logout request",
                                    value = """
                                            {
                                              "refreshToken": "<refresh-token>"
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Logout successful",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    name = "Logout response",
                                    value = """
                                            {
                                              "success": true,
                                              "message": "Logout successful",
                                              "data": {
                                                "message": "Logout successful"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Logout request validation failed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "Validation error",
                                    value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Validation failed",
                                              "message": "Please correct the invalid request fields.",
                                              "errors": {
                                                "refreshToken": "Refresh token is required"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Refresh token is invalid, expired, or revoked",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "Invalid refresh token",
                                    value = """
                                            {
                                              "success": false,
                                              "status": 401,
                                              "error": "Unauthorized",
                                              "message": "The token is invalid, expired, or revoked."
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(
            @Valid @org.springframework.web.bind.annotation.RequestBody
            RefreshTokenRequest request
    ) {
        authService.logout(request.getRefreshToken());

        LogoutResponse response = new LogoutResponse("Logout successful");

        return ResponseEntity
                .ok(ApiResponse.success("Logout successful", response));
    }
}