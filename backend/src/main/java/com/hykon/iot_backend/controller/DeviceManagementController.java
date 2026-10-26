package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.dto.RegisterDeviceRequest;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DeviceRegistrationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/management/devices")
public class DeviceManagementController {

    private final DeviceRegistrationService registrationService;

    public DeviceManagementController(
            DeviceRegistrationService registrationService
    ) {
        this.registrationService = registrationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> register(
            @Valid @RequestBody RegisterDeviceRequest request,
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Administrator access is required");
        }

        DeviceResponse response = registrationService.register(
                request,
                principal.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Device registered successfully",
                        response
                ));
    }
}