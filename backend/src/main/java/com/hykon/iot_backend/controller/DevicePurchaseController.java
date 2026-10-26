package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.DevicePurchaseResponse;
import com.hykon.iot_backend.dto.RecordDevicePurchaseRequest;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DevicePurchaseService;

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
@RequestMapping("/api/v1/management/device-purchases")
public class DevicePurchaseController {

    private final DevicePurchaseService purchaseService;

    public DevicePurchaseController(DevicePurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DevicePurchaseResponse>> record(
            @Valid @RequestBody RecordDevicePurchaseRequest request,
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Administrator access is required");
        }

        DevicePurchaseResponse response = purchaseService.record(
                request,
                principal.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Device purchase eligibility recorded",
                        response
                ));
    }
}