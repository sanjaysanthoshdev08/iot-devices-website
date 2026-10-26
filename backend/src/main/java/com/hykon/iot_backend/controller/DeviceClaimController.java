package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.ClaimDeviceRequest;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DeviceClaimService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/user/devices/claim")
public class DeviceClaimController {

    private final DeviceClaimService claimService;

    public DeviceClaimController(DeviceClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> claim(
            @Valid @RequestBody ClaimDeviceRequest request,
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("USER authentication is required");
        }

        DeviceResponse response = claimService.claim(
                request,
                principal.getName()
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Device verified successfully",
                response
        ));
    }
}