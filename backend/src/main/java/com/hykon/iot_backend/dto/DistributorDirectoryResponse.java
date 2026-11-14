package com.hykon.iot_backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DistributorDirectoryResponse(
        Long id,
        String distributorCode,
        String name,
        String status,
        LocalDateTime createdAt,
        AdminSummary administrator,
        List<BranchHierarchy> branches,
        String state,
        String district
) {
    public record AdminSummary(
            Long id,
            String name,
            String email,
            String status
    ) {}

    public record BranchHierarchy(
            Long id,
            String branchCode,
            String name,
            String location,
            String status,
            List<DeviceSummary> devices,
            String pincode,
            String branchAdminName,
            String branchAdminEmail
    ) {
        public BranchHierarchy(
                Long id, String branchCode, String name, String location,
                String status, List<DeviceSummary> devices
        ) {
            this(id, branchCode, name, location, status, devices, null, null, null);
        }

        public BranchHierarchy(
                Long id, String branchCode, String name, String location,
                String status, List<DeviceSummary> devices, String pincode
        ) {
            this(id, branchCode, name, location, status, devices, pincode, null, null);
        }
    }

    public record DeviceSummary(
            Long id,
            String deviceCode,
            String serialNumber,
            String systemName,
            String model,
            String status,
            List<String> customerEmails
    ) {}
}

