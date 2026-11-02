package com.hykon.iot_backend.security;

import com.hykon.iot_backend.config.SecurityConfig;
import com.hykon.iot_backend.controller.DeviceTelemetryCredentialController;
import com.hykon.iot_backend.dto.IssuedDeviceCredential;
import com.hykon.iot_backend.exception.GlobalExceptionHandler;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.service.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DeviceTelemetryCredentialController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        SecurityAndValidationTests.NoDefaultUserConfiguration.class
})
class DeviceTelemetryCredentialApiTests {

    private static final String ENDPOINT =
            "/api/v1/management/devices/1/telemetry-credential";

    @Autowired private MockMvc mvc;
    @MockitoBean private DeviceTelemetryCredentialService service;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private LoginSessionService loginSessionService;
    @MockitoBean private UserRepository userRepository;

    @Test
    void anonymousCannotIssueOrRevoke() throws Exception {
        mvc.perform(post(ENDPOINT)).andExpect(status().isUnauthorized());
        mvc.perform(delete(ENDPOINT)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void customerCannotIssueOrRevoke() throws Exception {
        mvc.perform(post(ENDPOINT).with(user("customer").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete(ENDPOINT).with(user("customer").roles("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HYKON_ADMIN", "DISTRIBUTOR_ADMIN"})
    void administratorsReceiveCredentialWithoutCaching(String role)
            throws Exception {
        UUID keyId = UUID.randomUUID();
        String testSecret = DeviceSecretCodec.generate();

        when(service.issueOrRotate(any(), eq(1L)))
                .thenReturn(new IssuedDeviceCredential(
                        keyId, 1L, "HALO1200-1", testSecret));

        mvc.perform(post(ENDPOINT)
                        .with(user("admin@example.com").roles(role)))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keyId").value(keyId.toString()))
                .andExpect(jsonPath("$.data.deviceId").value(1))
                .andExpect(jsonPath("$.data.deviceCode").value("HALO1200-1"))
                .andExpect(jsonPath("$.data.secret").value(testSecret))
                .andExpect(jsonPath("$.data.secretHash").doesNotExist());

        verify(service).issueOrRotate(
                argThat(auth -> "admin@example.com".equals(auth.getName())),
                eq(1L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HYKON_ADMIN", "DISTRIBUTOR_ADMIN"})
    void administratorsCanRevokeWithoutReceivingSecrets(String role)
            throws Exception {
        when(service.revoke(any(), eq(1L))).thenReturn(1);

        mvc.perform(delete(ENDPOINT)
                        .with(user("admin@example.com").roles(role)))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.deviceId").value(1))
                .andExpect(jsonPath("$.data.revokedCount").value(1))
                .andExpect(jsonPath("$.data.secret").doesNotExist())
                .andExpect(jsonPath("$.data.secretHash").doesNotExist());
    }

    @Test
    void serviceGateDenialNeverReturnsCredential() throws Exception {
        when(service.issueOrRotate(any(), eq(1L))).thenThrow(
                new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Access verification is required"));

        mvc.perform(post(ENDPOINT)
                        .with(user("distributor").roles("DISTRIBUTOR_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("Access verification is required"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void outOfScopeDeviceReturnsNotFound() throws Exception {
        when(service.issueOrRotate(any(), eq(1L))).thenThrow(
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Device not found"));

        mvc.perform(post(ENDPOINT)
                        .with(user("distributor").roles("DISTRIBUTOR_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void expiredSessionFailureReturnsUnauthorized() throws Exception {
        when(service.issueOrRotate(any(), eq(1L))).thenThrow(
                new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Session expired"));

        mvc.perform(post(ENDPOINT)
                        .with(user("admin").roles("HYKON_ADMIN")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void repeatedRevocationCanReturnZero() throws Exception {
        when(service.revoke(any(), eq(1L))).thenReturn(0);

        mvc.perform(delete(ENDPOINT)
                        .with(user("admin").roles("HYKON_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revokedCount").value(0));
    }
}