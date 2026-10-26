package com.hykon.iot_backend.dto;

import com.hykon.iot_backend.entity.Device;
import com.hykon.iot_backend.entity.DistributorBranch;

import java.time.LocalDateTime;

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
        LocalDateTime createdAt
) {
    public static DeviceResponse from(Device device) {
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
                device.getCreatedAt()
        );
    }
}