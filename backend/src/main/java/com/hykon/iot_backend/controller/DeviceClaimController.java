package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.ClaimDeviceRequest;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.SessionAccessVerificationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user/devices/claim")
public class DeviceClaimController {

    private final SessionAccessVerificationService claimService;

    public DeviceClaimController(
            SessionAccessVerificationService claimService
    ) {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> claim(
            @Valid @RequestBody ClaimDeviceRequest request,
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || !(authentication.getDetails() instanceof UUID sessionId)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "An authenticated login session is required"
            );
        }

        DeviceResponse response = claimService.verifyDevice(
                request,
                authentication.getName(),
                sessionId
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Device verified successfully",
                response
        ));
    }
}