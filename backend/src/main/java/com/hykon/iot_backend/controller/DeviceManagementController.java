package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.AuthorizeCustomerRequest;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.dto.RegisterDeviceRequest;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DeviceCustomerAccessService;
import com.hykon.iot_backend.service.DeviceRegistrationService;
import com.hykon.iot_backend.service.SessionAccessPolicyService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/management/devices")
public class DeviceManagementController {

    private final DeviceRegistrationService registrationService;
    private final SessionAccessPolicyService accessPolicy;
    private final DeviceCustomerAccessService customerAccessService;

    @org.springframework.beans.factory.annotation.Autowired
    public DeviceManagementController(
            DeviceRegistrationService registrationService,
            SessionAccessPolicyService accessPolicy,
            DeviceCustomerAccessService customerAccessService
    ) {
        this.registrationService = registrationService;
        this.accessPolicy = accessPolicy;
        this.customerAccessService = customerAccessService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> register(
            @Valid @RequestBody RegisterDeviceRequest request,
            Authentication principal
    ) {
        accessPolicy.requireVerified(principal);

        DeviceResponse response = registrationService.register(
                request,
                principal.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Device registered successfully",
                        response
                ));
    }

    @PostMapping("/{deviceId}/customers")
    public ResponseEntity<ApiResponse<DeviceResponse>> authorizeCustomer(
            @PathVariable Long deviceId,
            @Valid @RequestBody AuthorizeCustomerRequest request,
            Authentication principal
    ) {
        accessPolicy.requireVerified(principal);

        if (customerAccessService == null) {
            throw new IllegalStateException("Customer access service is not configured");
        }

        DeviceResponse response = customerAccessService.authorizeCustomer(
                deviceId,
                request,
                principal.getName()
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Customer authorized successfully",
                response
        ));
    }

    @DeleteMapping("/{deviceId}/customers/{customerUserId}")
    public ResponseEntity<ApiResponse<Void>> removeCustomerAccess(
            @PathVariable Long deviceId,
            @PathVariable Long customerUserId,
            Authentication principal
    ) {
        accessPolicy.requireVerified(principal);

        if (customerAccessService == null) {
            throw new IllegalStateException("Customer access service is not configured");
        }

        customerAccessService.removeCustomerAccess(
                deviceId,
                customerUserId,
                principal.getName()
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Customer access removed successfully",
                null
        ));
    }
}