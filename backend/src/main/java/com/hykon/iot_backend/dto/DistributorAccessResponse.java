package com.hykon.iot_backend.dto;

public record DistributorAccessResponse(
        Long distributorId,
        String distributorCode,
        String distributorName,
        Long branchId,
        String branchCode,
        String branchName,
        String location
) {
}