package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.dto.RegisterDeviceRequest;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DeviceRegistrationService;
import com.hykon.iot_backend.service.SessionAccessPolicyService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/api/v1/management/devices")
public class DeviceManagementController {

    private final DeviceRegistrationService registrationService;
    private final SessionAccessPolicyService accessPolicy;

    public DeviceManagementController(
            DeviceRegistrationService registrationService,
            SessionAccessPolicyService accessPolicy
    ) {
        this.registrationService = registrationService;
        this.accessPolicy = accessPolicy;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> register(
            @Valid @RequestBody RegisterDeviceRequest request,
            Authentication principal
    ) {
        accessPolicy.requireVerified(principal);

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