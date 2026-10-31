package com.hykon.iot_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DistributorAccessRequest {

    @Size(max = 150, message = "Distributor name must not exceed 150 characters")
    private String distributorName;

    @Size(max = 150, message = "Branch name must not exceed 150 characters")
    private String branchName;

    @NotBlank(message = "Location is required")
    @Size(max = 150, message = "Location must not exceed 150 characters")
    private String location;

    // Optional legacy fields for backward compatibility
    @NotBlank(message = "Distributor code is required")
    @Size(max = 100, message = "Distributor code must not exceed 100 characters")
    private String distributorCode;

    @NotBlank(message = "Branch code is required")
    @Size(max = 100, message = "Branch code must not exceed 100 characters")
    private String branchCode;

    public DistributorAccessRequest() {
    }

    public DistributorAccessRequest(String distributorName, String branchName, String location) {
        this.distributorName = distributorName;
        this.branchName = branchName;
        this.location = location;
        this.distributorCode = distributorName;
        this.branchCode = branchName;
    }

    public String getDistributorName() {
        return distributorName;
    }

    public void setDistributorName(String distributorName) {
        this.distributorName = distributorName;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDistributorCode() {
        return distributorCode;
    }

    public void setDistributorCode(String distributorCode) {
        this.distributorCode = distributorCode;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }
}