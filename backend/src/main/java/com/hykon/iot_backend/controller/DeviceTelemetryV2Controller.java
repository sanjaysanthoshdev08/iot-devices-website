package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.config.DeviceTelemetryV2SecurityConfig;
import com.hykon.iot_backend.dto.TelemetryV2Request;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DeviceTelemetryIngestionService;
import com.hykon.iot_backend.service.DeviceTelemetryV2IngestionService;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Universal telemetry endpoint: POST /api/v2/telemetry/readings (device credentials in headers). */
@RestController
@RequestMapping(DeviceTelemetryV2SecurityConfig.PATH)
public class DeviceTelemetryV2Controller {

    private final DeviceTelemetryV2IngestionService ingestion;

    public DeviceTelemetryV2Controller(DeviceTelemetryV2IngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DeviceTelemetryIngestionService.Receipt>> receive(
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody TelemetryV2Request request
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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device credential");
        }
        return values.get(0);
    }
}
