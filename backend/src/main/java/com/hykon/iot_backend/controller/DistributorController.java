package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.CreateDistributorRequest;
import com.hykon.iot_backend.dto.DistributorDirectoryResponse;
import com.hykon.iot_backend.dto.DistributorResponse;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DistributorService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

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

    @GetMapping
    public ResponseEntity<ApiResponse<List<DistributorDirectoryResponse>>> getDirectory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "20") int branchLimit,
            @RequestParam(defaultValue = "25") int deviceLimit,
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("HYKON_ADMIN access is required");
        }

        List<DistributorDirectoryResponse> directory = distributorService.getDirectory(
                principal.getName(), page, size, branchLimit, deviceLimit
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Distributor directory retrieved successfully",
                directory
        ));
    }

    @GetMapping("/{distributorId}/branches")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<DistributorDirectoryResponse.BranchHierarchy>>> getDistributorBranches(
            @PathVariable Long distributorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("HYKON_ADMIN access is required");
        }

        org.springframework.data.domain.Page<DistributorDirectoryResponse.BranchHierarchy> branches =
                distributorService.getDistributorBranches(distributorId, principal.getName(), page, size);

        return ResponseEntity.ok(ApiResponse.success(
                "Branches retrieved successfully",
                branches
        ));
    }

    @GetMapping("/branches/{branchId}/devices")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<DistributorDirectoryResponse.DeviceSummary>>> getBranchDevices(
            @PathVariable Long branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("HYKON_ADMIN access is required");
        }

        org.springframework.data.domain.Page<DistributorDirectoryResponse.DeviceSummary> devices =
                distributorService.getBranchDevices(branchId, principal.getName(), page, size);

        return ResponseEntity.ok(ApiResponse.success(
                "Devices retrieved successfully",
                devices
        ));
    }
}