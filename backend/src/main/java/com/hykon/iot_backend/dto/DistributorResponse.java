package com.hykon.iot_backend.dto;

import com.hykon.iot_backend.entity.Distributor;
import com.hykon.iot_backend.entity.DistributorBranch;

public record DistributorResponse(
        Long id,
        String distributorCode,
        String name,
        Long administratorUserId,
        String status,
        Long branchId,
        String branchCode,
        String branchName,
        String location,
        String branchStatus
) {
    public static DistributorResponse from(
            Distributor distributor,
            DistributorBranch branch
    ) {
        return new DistributorResponse(
                distributor.getId(),
                distributor.getDistributorCode(),
                distributor.getName(),
                distributor.getAdministrator().getId(),
                distributor.getStatus(),
                branch.getId(),
                branch.getBranchCode(),
                branch.getName(),
                branch.getLocation(),
                branch.getStatus()
        );
    }
}