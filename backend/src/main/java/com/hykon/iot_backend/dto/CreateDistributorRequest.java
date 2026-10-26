package com.hykon.iot_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreateDistributorRequest {

    @NotBlank(message = "Distributor ID is required")
    @Size(max = 100, message = "Distributor ID must not exceed 100 characters")
    private String distributorCode;

    @NotBlank(message = "Distributor name is required")
    @Size(max = 150, message = "Distributor name must not exceed 150 characters")
    private String name;

    @NotNull(message = "Distributor administrator account is required")
    @Positive(message = "Administrator account ID must be positive")
    private Long administratorUserId;

    @NotBlank(message = "Branch code is required")
    @Size(max = 100, message = "Branch code must not exceed 100 characters")
    private String branchCode;

    @NotBlank(message = "Branch name is required")
    @Size(max = 150, message = "Branch name must not exceed 150 characters")
    private String branchName;

    @NotBlank(message = "Location is required")
    @Size(max = 150, message = "Location must not exceed 150 characters")
    private String location;

    public CreateDistributorRequest() {
    }

    public String getDistributorCode() { return distributorCode; }
    public void setDistributorCode(String distributorCode) {
        this.distributorCode = distributorCode;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getAdministratorUserId() { return administratorUserId; }
    public void setAdministratorUserId(Long administratorUserId) {
        this.administratorUserId = administratorUserId;
    }

    public String getBranchCode() { return branchCode; }
    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getLocation() { return location; }
    public void setLocation(String location) {
        this.location = location;
    }
}