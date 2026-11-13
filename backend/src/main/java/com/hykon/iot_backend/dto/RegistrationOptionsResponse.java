package com.hykon.iot_backend.dto;

import java.util.List;

/** Distributors and branches an admin may pick from when registering a system or creating a branch. */
public record RegistrationOptionsResponse(List<DistributorOption> distributors) {

    public record DistributorOption(
            Long id,
            String name,
            String distributorCode,
            String state,
            String district,
            List<BranchOption> branches
    ) {
    }

    public record BranchOption(
            Long id,
            String name,
            String branchCode,
            String location,
            String address,
            String pincode
    ) {
    }
}
