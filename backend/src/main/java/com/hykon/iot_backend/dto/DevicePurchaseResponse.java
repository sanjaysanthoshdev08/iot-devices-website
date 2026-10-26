package com.hykon.iot_backend.dto;

import com.hykon.iot_backend.entity.DevicePurchase;

import java.time.LocalDateTime;

public record DevicePurchaseResponse(
        Long id,
        Long deviceId,
        String deviceCode,
        Long customerUserId,
        Long recordedByUserId,
        String purchaseReference,
        String status,
        LocalDateTime claimedAt,
        LocalDateTime createdAt
) {
    public static DevicePurchaseResponse from(DevicePurchase purchase) {
        return new DevicePurchaseResponse(
                purchase.getId(),
                purchase.getDevice().getId(),
                purchase.getDevice().getDeviceCode(),
                purchase.getCustomer().getId(),
                purchase.getRecordedBy().getId(),
                purchase.getPurchaseReference(),
                purchase.getStatus(),
                purchase.getClaimedAt(),
                purchase.getCreatedAt()
        );
    }
}