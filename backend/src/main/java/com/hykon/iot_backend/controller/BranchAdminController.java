package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.CreateBranchWithAdminRequest;
import com.hykon.iot_backend.dto.DistributorDirectoryResponse;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.BranchAdminService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class BranchAdminController {

    private final BranchAdminService branchAdminService;

    public BranchAdminController(BranchAdminService branchAdminService) {
        this.branchAdminService = branchAdminService;
    }

    /** HYKON_ADMIN: create a branch and its branch admin under any distributor. */
    @PostMapping("/api/v1/admin/distributors/{distributorId}/branch-with-admin")
    public ResponseEntity<ApiResponse<DistributorDirectoryResponse.BranchHierarchy>> createForDistributor(
            @PathVariable Long distributorId,
            @Valid @RequestBody CreateBranchWithAdminRequest request,
            Principal principal
    ) {
        return respond(branchAdminService.create(distributorId, request, name(principal)));
    }

    /** HYKON_ADMIN: create a branch and its branch admin, identifying the distributor by its code. */
    @PostMapping("/api/v1/admin/branches")
    public ResponseEntity<ApiResponse<DistributorDirectoryResponse.BranchHierarchy>> createByCode(
            @Valid @RequestBody CreateBranchWithAdminRequest request,
            Principal principal
    ) {
        return respond(branchAdminService.create(null, request, name(principal)));
    }

    /** DISTRIBUTOR_ADMIN: create a branch and its branch admin in their own distributor. */
    @PostMapping("/api/v1/distributor/branches")
    public ResponseEntity<ApiResponse<DistributorDirectoryResponse.BranchHierarchy>> createOwn(
            @Valid @RequestBody CreateBranchWithAdminRequest request,
            Principal principal
    ) {
        return respond(branchAdminService.create(null, request, name(principal)));
    }

    /** HYKON_ADMIN or the owning DISTRIBUTOR_ADMIN: create the login for an existing branch. */
    @PostMapping("/api/v1/management/branches/{branchId}/admin")
    public ResponseEntity<ApiResponse<DistributorDirectoryResponse.BranchHierarchy>> assignAdmin(
            @PathVariable Long branchId,
            @Valid @RequestBody com.hykon.iot_backend.dto.AssignBranchAdminRequest request,
            Principal principal
    ) {
        return respond(branchAdminService.assignAdmin(branchId, request, name(principal)));
    }

    private String name(Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Administrator access is required");
        }
        return principal.getName();
    }

    private ResponseEntity<ApiResponse<DistributorDirectoryResponse.BranchHierarchy>> respond(
            DistributorDirectoryResponse.BranchHierarchy branch
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Branch and branch admin created successfully", branch));
    }
}
