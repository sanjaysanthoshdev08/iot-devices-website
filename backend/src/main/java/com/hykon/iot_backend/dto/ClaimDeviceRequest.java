package com.hykon.iot_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ClaimDeviceRequest {

    @NotBlank(message = "Device ID is required")
    @Size(max = 100, message = "Device ID must not exceed 100 characters")
    private String deviceCode;

    @NotBlank(message = "Serial number is required")
    @Size(max = 150, message = "Serial number must not exceed 150 characters")
    private String serialNumber;

    public ClaimDeviceRequest() {
    }

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }
}