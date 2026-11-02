package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.IssuedDeviceCredential;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DeviceTelemetryCredentialService;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/management/devices/{deviceId}/telemetry-credential")
public class DeviceTelemetryCredentialController {

    private final DeviceTelemetryCredentialService service;

    public DeviceTelemetryCredentialController(
            DeviceTelemetryCredentialService service
    ) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IssuedDeviceCredential>> issueOrRotate(
            @PathVariable("deviceId") Long deviceId,
            Authentication authentication
    ) {
        var issued = service.issueOrRotate(authentication, deviceId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(
                        "Device credential issued. Save the secret securely; "
                                + "it cannot be retrieved later. "
                                + "Any previous credential has been revoked.",
                        issued));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<RevocationResult>> revoke(
            @PathVariable("deviceId") Long deviceId,
            Authentication authentication
    ) {
        int revoked = service.revoke(authentication, deviceId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(
                        "Device credential revocation completed",
                        new RevocationResult(deviceId, revoked)));
    }

    public record RevocationResult(Long deviceId, int revokedCount) {}
}