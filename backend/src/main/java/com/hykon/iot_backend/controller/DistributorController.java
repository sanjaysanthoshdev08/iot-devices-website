package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.CreateDistributorRequest;
import com.hykon.iot_backend.dto.DistributorResponse;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DistributorService;

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
@RequestMapping("/api/v1/admin/distributors")
public class DistributorController {

    private final DistributorService distributorService;

    public DistributorController(DistributorService distributorService) {
        this.distributorService = distributorService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DistributorResponse>> create(
            @Valid @RequestBody CreateDistributorRequest request,
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("HYKON_ADMIN access is required");
        }

        DistributorResponse response = distributorService.create(
                request,
                principal.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Distributor and first branch created successfully",
                        response
                ));
    }
}