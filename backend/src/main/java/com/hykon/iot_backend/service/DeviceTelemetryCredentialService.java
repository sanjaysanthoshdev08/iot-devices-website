package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.IssuedDeviceCredential;
import com.hykon.iot_backend.entity.Device;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.DeviceRepository;
import com.hykon.iot_backend.repository.DeviceTelemetryCredentialStore;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.security.DeviceSecretCodec;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Transactional
public class DeviceTelemetryCredentialService {

    private final SessionAccessPolicyService accessPolicy;
    private final UserRepository users;
    private final DeviceQueryService deviceQueries;
    private final DeviceRepository devices;
    private final DeviceTelemetryCredentialStore credentials;

    public DeviceTelemetryCredentialService(
            SessionAccessPolicyService accessPolicy,
            UserRepository users,
            DeviceQueryService deviceQueries,
            DeviceRepository devices,
            DeviceTelemetryCredentialStore credentials
    ) {
        this.accessPolicy = accessPolicy;
        this.users = users;
        this.deviceQueries = deviceQueries;
        this.devices = devices;
        this.credentials = credentials;
    }

    /**
     * First issuance or explicit rotation. Device locking serializes rotations.
     * Revocation and insertion share one transaction: insertion failure must
     * roll back revocation of the previous credential.
     */
    public IssuedDeviceCredential issueOrRotate(
            Authentication authentication, Long deviceId
    ) {
        User administrator = requireAdministrator(authentication);
        Device device = lockAuthorizedDevice(authentication, deviceId);

        if (!"ACTIVE".equalsIgnoreCase(device.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Credentials can only be issued for an active device");
        }

        UUID keyId = UUID.randomUUID();
        String secret = DeviceSecretCodec.generate();

        credentials.revokeActiveForDevice(deviceId);
        credentials.insert(
                keyId, deviceId, DeviceSecretCodec.hash(secret),
                administrator.getId()
        );

        return new IssuedDeviceCredential(
                keyId, deviceId, device.getDeviceCode(), secret);
    }

    public int revoke(Authentication authentication, Long deviceId) {
        requireAdministrator(authentication);
        lockAuthorizedDevice(authentication, deviceId);

        // Revocation is allowed even when a device is inactive.
        return credentials.revokeActiveForDevice(deviceId);
    }

    private User requireAdministrator(Authentication authentication) {
        accessPolicy.requireVerified(authentication);

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "An authenticated login session is required");
        }

        User account = users.findByEmailWithRole(authentication.getName())
                .orElseThrow(this::forbidden);

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())
                || account.getId() == null
                || account.getRole() == null
                || (account.getRole().getName() != RoleName.HYKON_ADMIN
                    && account.getRole().getName() != RoleName.DISTRIBUTOR_ADMIN)) {
            throw forbidden();
        }

        return account;
    }

    private Device lockAuthorizedDevice(
            Authentication authentication, Long deviceId
    ) {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException(
                    "Device ID must be a positive number");
        }

        Device device = devices.findByIdForUpdate(deviceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Device not found"));

        // Reuse the existing authoritative administrator/device scope.
        deviceQueries.get(deviceId, authentication.getName());
        return device;
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Administrator device access is required");
    }
}