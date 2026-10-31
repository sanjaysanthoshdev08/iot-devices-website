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
        List<BranchHierarchy> branches
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
            List<DeviceSummary> devices
    ) {}

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

