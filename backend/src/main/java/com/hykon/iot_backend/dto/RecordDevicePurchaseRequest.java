package com.hykon.iot_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class RecordDevicePurchaseRequest {

    @NotNull(message = "Registered device ID is required")
    @Positive(message = "Registered device ID must be positive")
    private Long deviceId;

    @Positive(message = "Customer account ID must be positive")
    private Long customerUserId;

    @jakarta.validation.constraints.Email(message = "Valid customer email is required")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String customerEmail;

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

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