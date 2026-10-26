package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.RegisterResponse;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.response.ApiResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final UserRepository userRepository;

    public ProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ApiResponse<RegisterResponse> currentAccount(
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        User user = userRepository.findByEmailWithRole(principal.getName())
                .orElseThrow(() ->
                        new AccessDeniedException("Account is unavailable"));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())
                || user.getRole() == null
                || user.getRole().getName() == null) {
            throw new AccessDeniedException("Account is unavailable");
        }

        return ApiResponse.success(
                "Account profile retrieved successfully",
                new RegisterResponse(user)
        );
    }
}