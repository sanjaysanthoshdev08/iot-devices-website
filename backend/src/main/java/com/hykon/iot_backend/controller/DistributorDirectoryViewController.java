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

    public DistributorDirectoryViewController(DistributorService distributorService) {
        this.distributorService = distributorService;
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
