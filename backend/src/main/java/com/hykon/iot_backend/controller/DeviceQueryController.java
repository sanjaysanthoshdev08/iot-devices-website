package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.DevicePageResponse;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DeviceQueryService;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceQueryController {

    private final DeviceQueryService queryService;

    public DeviceQueryController(DeviceQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DevicePageResponse>> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Principal principal
    ) {
        Page<DeviceResponse> devices = queryService.list(
                requireIdentity(principal), page, size
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Devices retrieved successfully",
                DevicePageResponse.from(devices)
        ));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<DeviceResponse>> get(
            @PathVariable("deviceId") Long deviceId,
            Principal principal
    ) {
        DeviceResponse device = queryService.get(
                deviceId, requireIdentity(principal)
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Device retrieved successfully",
                device
        ));
    }

    private String requireIdentity(Principal principal) {
        if (principal == null
                || principal.getName() == null
                || principal.getName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required"
            );
        }

        return principal.getName();
    }
}