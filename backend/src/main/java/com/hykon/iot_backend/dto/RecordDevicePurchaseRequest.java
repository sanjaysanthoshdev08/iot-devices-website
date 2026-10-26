package com.hykon.iot_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class RecordDevicePurchaseRequest {

    @NotNull(message = "Registered device ID is required")
    @Positive(message = "Registered device ID must be positive")
    private Long deviceId;

    @NotNull(message = "Customer account ID is required")
    @Positive(message = "Customer account ID must be positive")
    private Long customerUserId;

    @NotBlank(message = "Purchase reference is required")
    @Size(max = 150, message = "Purchase reference must not exceed 150 characters")
    private String purchaseReference;

    public RecordDevicePurchaseRequest() {
    }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public Long getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(Long customerUserId) {
        this.customerUserId = customerUserId;
    }

    public String getPurchaseReference() { return purchaseReference; }
    public void setPurchaseReference(String purchaseReference) {
        this.purchaseReference = purchaseReference;
    }
}