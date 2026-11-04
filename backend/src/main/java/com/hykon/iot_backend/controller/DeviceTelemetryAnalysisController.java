package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.TelemetryAnalysisPeriod;
import com.hykon.iot_backend.dto.TelemetryAnalysisResponse;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DeviceTelemetryAnalysisService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/telemetry/analysis")
public class DeviceTelemetryAnalysisController {
    private final DeviceTelemetryAnalysisService service;

    public DeviceTelemetryAnalysisController(DeviceTelemetryAnalysisService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<TelemetryAnalysisResponse>> analyze(
            @PathVariable("deviceId") Long deviceId,
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam("simulated") boolean simulated,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication
    ) {
        var period = new TelemetryAnalysisPeriod(
                Instant.parse(from), Instant.parse(to), simulated);

        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(
                        "Telemetry analysis retrieved",
                        service.analyze(deviceId, period, page, size, authentication)));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            DateTimeParseException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<Map<String, Object>> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "status", 400,
                "message",
                "Provide valid UTC timestamps, simulation selection and pagination. "
                        + "Start must precede end; maximum period is 366 days."));
    }
}