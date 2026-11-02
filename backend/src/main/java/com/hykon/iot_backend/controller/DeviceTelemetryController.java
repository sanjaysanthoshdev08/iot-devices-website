package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.config.DeviceTelemetrySecurityConfig;
import com.hykon.iot_backend.dto.TelemetryRequest;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DeviceTelemetryIngestionService;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(DeviceTelemetrySecurityConfig.PATH)
public class DeviceTelemetryController {

    private final DeviceTelemetryIngestionService ingestion;

    public DeviceTelemetryController(
            DeviceTelemetryIngestionService ingestion
    ) {
        this.ingestion = ingestion;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DeviceTelemetryIngestionService.Receipt>>
    receive(
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody TelemetryRequest request
    ) {
        String keyId = singleHeader(headers, "X-Device-Key-Id");
        String secret = singleHeader(headers, "X-Device-Secret");

        var receipt = ingestion.ingest(keyId, secret, request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(
                        receipt.duplicate()
                                ? "Telemetry retry acknowledged"
                                : "Telemetry accepted",
                        receipt));
    }

    private static String singleHeader(HttpHeaders headers, String name) {
        var values = headers.get(name);
        if (values == null || values.size() != 1
                || values.get(0) == null || values.get(0).isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid device credential");
        }
        return values.get(0);
    }
}