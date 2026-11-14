package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.DistributorDirectoryResponse;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DistributorService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
public class DistributorDirectoryViewController {

    private final DistributorService distributorService;
    private final com.hykon.iot_backend.service.DistributorProvisioningService provisioningService;
    private final com.hykon.iot_backend.service.DistributorAdminService distributorAdminService;

    public DistributorDirectoryViewController(
            DistributorService distributorService,
            com.hykon.iot_backend.service.DistributorProvisioningService provisioningService,
            com.hykon.iot_backend.service.DistributorAdminService distributorAdminService
    ) {
        this.distributorService = distributorService;
        this.provisioningService = provisioningService;
        this.distributorAdminService = distributorAdminService;
    }

    /** Overseeing Distributor Admin: distributor logins that can be chosen when registering a distributor. */
    @GetMapping("/api/v1/distributor/logins")
    public ResponseEntity<ApiResponse<List<com.hykon.iot_backend.dto.DistributorAdminOption>>> logins(
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Distributor administrator access is required");
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Distributor logins",
                distributorAdminService.listAvailable(principal.getName())));
    }

    /** Overseeing Distributor Admin: register a distributor organization with its initial branch. */
    @org.springframework.web.bind.annotation.PostMapping("/api/v1/distributor/distributors/register")
    public ResponseEntity<ApiResponse<com.hykon.iot_backend.dto.DistributorResponse>> registerDistributor(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
            com.hykon.iot_backend.dto.CreateDistributorRequest request,
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Distributor administrator access is required");
        }
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(ApiResponse.success(
                "Distributor and first branch created successfully",
                distributorService.create(request, principal.getName())));
    }

    /** Distributor Admin: create a distributor organization together with its own email/password login. */
    @org.springframework.web.bind.annotation.PostMapping("/api/v1/distributor/distributors")
    public ResponseEntity<ApiResponse<com.hykon.iot_backend.dto.DistributorResponse>> createDistributor(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
            com.hykon.iot_backend.dto.CreateDistributorWithLoginRequest request,
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Distributor administrator access is required");
        }
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(ApiResponse.success(
                "Distributor and login created successfully",
                provisioningService.create(request, principal.getName())));
    }

    /** Distributor Admin: read-only list of every registered distributor and its branches. */
    @GetMapping("/api/v1/distributor/directory")
    public ResponseEntity<ApiResponse<List<DistributorDirectoryResponse>>> directory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "50") int branchLimit,
            @RequestParam(defaultValue = "25") int deviceLimit,
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Distributor administrator access is required");
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Distributor directory retrieved successfully",
                distributorService.getDirectoryForViewer(
                        principal.getName(), page, size, branchLimit, deviceLimit)));
    }
}
