package com.hykon.iot_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class RegisterDeviceRequest {

    @NotBlank(message = "Device ID is required")
    @Size(max = 100, message = "Device ID must not exceed 100 characters")
    private String deviceCode;

    @NotBlank(message = "Serial number is required")
    @Size(max = 150, message = "Serial number must not exceed 150 characters")
    private String serialNumber;

    @NotBlank(message = "System name is required")
    @Size(max = 150, message = "System name must not exceed 150 characters")
    private String systemName;

    @Size(max = 100, message = "Model must not exceed 100 characters")
    private String model;

    @Positive(message = "Branch ID must be positive")
    private Long branchId;

    @Size(max = 100, message = "Distributor code must not exceed 100 characters")
    private String distributorCode;

    @Size(max = 100, message = "Branch code must not exceed 100 characters")
    private String branchCode;

    public String getDistributorCode() { return distributorCode; }
    public void setDistributorCode(String distributorCode) { this.distributorCode = distributorCode; }

    public String getBranchCode() { return branchCode; }
    public void setBranchCode(String branchCode) { this.branchCode = branchCode; }

    public RegisterDeviceRequest() {
    }

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
}