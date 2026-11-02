package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.IssuedDeviceCredential;
import com.hykon.iot_backend.entity.Device;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.DeviceRepository;
import com.hykon.iot_backend.repository.DeviceTelemetryCredentialStore;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.security.DeviceSecretCodec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceTelemetryCredentialServiceTest {

    private final SessionAccessPolicyService policy =
            mock(SessionAccessPolicyService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DeviceQueryService queries = mock(DeviceQueryService.class);
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final DeviceTelemetryCredentialStore store =
            mock(DeviceTelemetryCredentialStore.class);

    private final DeviceTelemetryCredentialService service =
            new DeviceTelemetryCredentialService(
                    policy, users, queries, devices, store);

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken(
                    "admin@example.com", null, List.of());

    private User administrator;
    private Device device;

    @BeforeEach
    void setup() {
        administrator = mock(User.class, RETURNS_DEEP_STUBS);
        when(administrator.getId()).thenReturn(5L);
        when(administrator.getStatus()).thenReturn("ACTIVE");
        when(administrator.getRole().getName())
                .thenReturn(RoleName.HYKON_ADMIN);
        when(users.findByEmailWithRole("admin@example.com"))
                .thenReturn(Optional.of(administrator));

        device = mock(Device.class);
        when(device.getStatus()).thenReturn("ACTIVE");
        when(device.getDeviceCode()).thenReturn("HALO1200-1");
        when(devices.findByIdForUpdate(1L))
                .thenReturn(Optional.of(device));
    }

    @Test
    void rotationStoresOnlyHashAfterGateScopeAndDeviceLock() {
        var issued = service.issueOrRotate(authentication, 1L);

        var order = inOrder(policy, devices, queries, store);
        order.verify(policy).requireVerified(authentication);
        order.verify(devices).findByIdForUpdate(1L);
        order.verify(queries).get(1L, "admin@example.com");
        order.verify(store).revokeActiveForDevice(1L);
        order.verify(store).insert(
                issued.keyId(), 1L,
                DeviceSecretCodec.hash(issued.secret()), 5L);

        assertEquals(1L, issued.deviceId());
        assertEquals("HALO1200-1", issued.deviceCode());
        assertEquals("IssuedDeviceCredential[redacted]", issued.toString());
    }

    @Test
    void unverifiedSessionCannotIssueOrRevoke() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(policy).requireVerified(authentication);

        assertThrows(ResponseStatusException.class,
                () -> service.issueOrRotate(authentication, 1L));
        assertThrows(ResponseStatusException.class,
                () -> service.revoke(authentication, 1L));

        verifyNoInteractions(devices, queries, store);
    }

    @Test
    void customerCannotIssueOrRevokeDespiteAuthenticatedSession() {
        when(administrator.getRole().getName()).thenReturn(RoleName.USER);

        assertThrows(ResponseStatusException.class,
                () -> service.issueOrRotate(authentication, 1L));
        assertThrows(ResponseStatusException.class,
                () -> service.revoke(authentication, 1L));

        verifyNoInteractions(devices, queries, store);
    }

    @Test
    void distributorCannotChangeCredentialsOutsideExistingDeviceScope() {
        when(administrator.getRole().getName())
                .thenReturn(RoleName.DISTRIBUTOR_ADMIN);
        when(queries.get(1L, "admin@example.com")).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThrows(ResponseStatusException.class,
                () -> service.issueOrRotate(authentication, 1L));
        assertThrows(ResponseStatusException.class,
                () -> service.revoke(authentication, 1L));

        verifyNoInteractions(store);
    }

    @Test
    void inactiveAccountCannotChangeCredentials() {
        when(administrator.getStatus()).thenReturn("DISABLED");

        assertThrows(ResponseStatusException.class,
                () -> service.issueOrRotate(authentication, 1L));
        assertThrows(ResponseStatusException.class,
                () -> service.revoke(authentication, 1L));

        verifyNoInteractions(devices, queries, store);
    }

    @Test
    void inactiveDeviceCannotReceiveNewCredentialButCanBeRevoked() {
        when(device.getStatus()).thenReturn("INACTIVE");

        var error = assertThrows(ResponseStatusException.class,
                () -> service.issueOrRotate(authentication, 1L));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(store);

        when(store.revokeActiveForDevice(1L)).thenReturn(1);
        assertEquals(1, service.revoke(authentication, 1L));
        verify(store, never()).insert(
                any(UUID.class), anyLong(), anyString(), anyLong());
    }

    @Test
    void missingDeviceDoesNotWriteCredentials() {
        when(devices.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        var error = assertThrows(ResponseStatusException.class,
                () -> service.issueOrRotate(authentication, 1L));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());

        verifyNoInteractions(store);
    }

    @Test
    void invalidDeviceIdDoesNotReachDeviceLookupOrStorage() {
        assertThrows(IllegalArgumentException.class,
                () -> service.issueOrRotate(authentication, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> service.revoke(authentication, null));

        verifyNoInteractions(devices, queries, store);
    }

    @Test
    void authorizedDistributorCanIssueForItsScopedDevice() {
        when(administrator.getRole().getName())
                .thenReturn(RoleName.DISTRIBUTOR_ADMIN);

        var issued = service.issueOrRotate(authentication, 1L);

        verify(queries).get(1L, "admin@example.com");
        verify(store).insert(
                issued.keyId(), 1L,
                DeviceSecretCodec.hash(issued.secret()), 5L);
    }
}