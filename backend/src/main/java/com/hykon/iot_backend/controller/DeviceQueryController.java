package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.DevicePageResponse;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DeviceQueryService;
import com.hykon.iot_backend.service.SessionAccessPolicyService;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceQueryController {

    private final DeviceQueryService queryService;
    private final SessionAccessPolicyService accessPolicy;

    public DeviceQueryController(
            DeviceQueryService queryService,
            SessionAccessPolicyService accessPolicy
    ) {
        this.queryService = queryService;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DevicePageResponse>> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication
    ) {
        accessPolicy.requireVerified(authentication);

        Page<DeviceResponse> devices = queryService.list(
                authentication.getName(), page, size
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Devices retrieved successfully",
                DevicePageResponse.from(devices)
        ));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<DeviceResponse>> get(
            @PathVariable("deviceId") Long deviceId,
            Authentication authentication
    ) {
        accessPolicy.requireVerified(authentication);

        DeviceResponse device = queryService.get(
                deviceId, authentication.getName()
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Device retrieved successfully",
                device
        ));
    }
}