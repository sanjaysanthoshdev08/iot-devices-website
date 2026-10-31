package com.hykon.iot_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthorizeCustomerRequest {

    @NotBlank(message = "Customer email is required")
    @Email(message = "Valid customer email is required")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String customerEmail;

    public AuthorizeCustomerRequest() {}

    public AuthorizeCustomerRequest(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
}

