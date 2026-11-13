package com.hykon.iot_backend.dto;

import com.hykon.iot_backend.entity.Device;
import com.hykon.iot_backend.entity.DistributorBranch;

import java.time.LocalDateTime;
import java.util.List;

public record DeviceResponse(
        Long id,
        String deviceCode,
        String serialNumber,
        String systemName,
        String model,
        Long branchId,
        Long distributorId,
        Long ownerUserId,
        String status,
        LocalDateTime createdAt,
        List<String> customerEmails,
        String distributorName,
        String branchName,
        String distributorCode,
        String state,
        String district,
        String branchCode,
        String branchLocation,
        String distributorAdminName,
        String distributorAdminEmail,
        String branchAdminName,
        String branchAdminEmail,
        String branchPincode
) {
    public DeviceResponse(
            Long id,
            String deviceCode,
            String serialNumber,
            String systemName,
            String model,
            Long branchId,
            Long distributorId,
            Long ownerUserId,
            String status,
            LocalDateTime createdAt,
            List<String> customerEmails,
            String distributorName,
            String branchName,
            String distributorCode,
            String state,
            String district,
            String branchCode,
            String branchLocation
    ) {
        this(id, deviceCode, serialNumber, systemName, model, branchId, distributorId, ownerUserId, status, createdAt, customerEmails, distributorName, branchName, distributorCode, state, district, branchCode, branchLocation, null, null, null, null, null);
    }

    public DeviceResponse(
            Long id,
            String deviceCode,
            String serialNumber,
            String systemName,
            String model,
            Long branchId,
            Long distributorId,
            Long ownerUserId,
            String status,
            LocalDateTime createdAt,
            List<String> customerEmails,
            String distributorName,
            String branchName
    ) {
        this(id, deviceCode, serialNumber, systemName, model, branchId, distributorId, ownerUserId, status, createdAt, customerEmails, distributorName, branchName, null, null, null, null, null);
    }

    public DeviceResponse(
            Long id,
            String deviceCode,
            String serialNumber,
            String systemName,
            String model,
            Long branchId,
            Long distributorId,
            Long ownerUserId,
            String status,
            LocalDateTime createdAt,
            List<String> customerEmails
    ) {
        this(id, deviceCode, serialNumber, systemName, model, branchId, distributorId, ownerUserId, status, createdAt, customerEmails, null, null);
    }

    public DeviceResponse(
            Long id,
            String deviceCode,
            String serialNumber,
            String systemName,
            String model,
            Long branchId,
            Long distributorId,
            Long ownerUserId,
            String status,
            LocalDateTime createdAt
    ) {
        this(id, deviceCode, serialNumber, systemName, model, branchId, distributorId, ownerUserId, status, createdAt, List.of(), null, null);
    }

    public static DeviceResponse from(Device device) {
        return from(device, List.of());
    }

    public static DeviceResponse from(Device device, List<String> customerEmails) {
        DistributorBranch branch = device.getBranch();

        return new DeviceResponse(
                device.getId(),
                device.getDeviceCode(),
                device.getSerialNumber(),
                device.getSystemName(),
                device.getModel(),
                branch == null ? null : branch.getId(),
                branch == null ? null : branch.getDistributor().getId(),
                device.getOwner() == null
                        ? null : device.getOwner().getId(),
                device.getStatus(),
                device.getCreatedAt(),
                customerEmails == null ? List.of() : List.copyOf(customerEmails),
                branch == null ? null : branch.getDistributor().getName(),
                branch == null ? null : branch.getName(),
                branch == null ? null : branch.getDistributor().getDistributorCode(),
                branch == null ? null : branch.getDistributor().getState(),
                branch == null ? null : branch.getDistributor().getDistrict(),
                branch == null ? null : branch.getBranchCode(),
                branch == null ? null : branch.getLocation(),
                branch == null || branch.getDistributor().getAdministrator() == null
                        ? null : branch.getDistributor().getAdministrator().getName(),
                branch == null || branch.getDistributor().getAdministrator() == null
                        ? null : branch.getDistributor().getAdministrator().getEmail(),
                branch == null || branch.getBranchAdmin() == null
                        ? null : branch.getBranchAdmin().getName(),
                branch == null || branch.getBranchAdmin() == null
                        ? null : branch.getBranchAdmin().getEmail(),
                branch == null ? null : branch.getPincode()
        );
    }
}