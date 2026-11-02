package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.TelemetryHistoryResponse;
import com.hykon.iot_backend.dto.TelemetryLatestResponse;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DeviceTelemetryQueryService;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/telemetry")
public class DeviceTelemetryQueryController {

    private final DeviceTelemetryQueryService service;

    public DeviceTelemetryQueryController(DeviceTelemetryQueryService service) {
        this.service = service;
    }

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<TelemetryLatestResponse>> latest(
            @PathVariable("deviceId") Long deviceId,
            Authentication authentication
    ) {
        var result = service.latest(deviceId, authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Latest telemetry retrieved", result));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<TelemetryHistoryResponse>> history(
            @PathVariable("deviceId") Long deviceId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication
    ) {
        var result = service.history(deviceId, page, size, authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Telemetry history retrieved", result));
    }

    @ExceptionHandler(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> invalidParameter() {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "status", 400,
                "message", "Device ID, page and size must be valid numbers."));
    }
}