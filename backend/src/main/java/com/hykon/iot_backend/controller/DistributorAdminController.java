package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.RegisterRequest;
import com.hykon.iot_backend.dto.RegisterResponse;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DistributorAdminService;

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
@RequestMapping("/api/v1/admin/distributor-admins")
public class DistributorAdminController {

    private final DistributorAdminService distributorAdminService;

    public DistributorAdminController(
            DistributorAdminService distributorAdminService
    ) {
        this.distributorAdminService = distributorAdminService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RegisterResponse>> create(
            @Valid @RequestBody RegisterRequest request,
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("HYKON_ADMIN access is required");
        }

        RegisterResponse response = distributorAdminService.create(
                request,
                principal.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Distributor administrator created",
                        response
                ));
    }
}