package com.hykon.iot_backend.security;

import com.hykon.iot_backend.config.*;
import com.hykon.iot_backend.controller.DeviceTelemetryQueryController;
import com.hykon.iot_backend.dto.*;
import com.hykon.iot_backend.exception.GlobalExceptionHandler;
import com.hykon.iot_backend.repository.*;
import com.hykon.iot_backend.service.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DeviceTelemetryQueryController.class)
@Import({
        SecurityConfig.class,
        DeviceTelemetrySecurityConfig.class,
        GlobalExceptionHandler.class,
        DeviceTelemetryQueryService.class,
        SecurityAndValidationTests.NoDefaultUserConfiguration.class
})
class DeviceTelemetryQueryApiTests {

    private static final String ROOT = "/api/v1/devices/101/telemetry";
    private static final String EMAIL = "reader@example.com";

    @Autowired private MockMvc mvc;

    @MockitoBean private JwtService jwtService;
    @MockitoBean private LoginSessionService loginSessions;
    @MockitoBean private UserRepository users;
    @MockitoBean private SessionAccessPolicyService policy;
    @MockitoBean private DeviceQueryService deviceQueries;
    @MockitoBean private DeviceTelemetryReadStore readings;

    private TelemetryReadingResponse reading(Instant received) {
        return new TelemetryReadingResponse(
                501L, 101L, 1, UUID.randomUUID(), 1L,
                null, received, 5000L, true,
                TelemetryRequest.OperatingMode.LINE,
                new TelemetryRequest.Battery(
                        new BigDecimal("51.2"), null, null),
                new TelemetryRequest.Grid(null, null, null, null),
                new TelemetryRequest.Load(null, null, null, null));
    }

    @Test
    void anonymousRequestsCannotReadLatestOrHistory() throws Exception {
        mvc.perform(get(ROOT + "/latest"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT + "/history"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(policy, deviceQueries, readings);
    }

    @Test
    void deviceCredentialHeadersCannotAuthorizeWebsiteReads() throws Exception {
        for (String suffix : new String[] {"/latest", "/history"}) {
            mvc.perform(get(ROOT + suffix)
                            .header("X-Device-Key-Id", UUID.randomUUID().toString())
                            .header("X-Device-Secret", DeviceSecretCodec.generate()))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(policy, deviceQueries, readings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "DISTRIBUTOR_ADMIN", "HYKON_ADMIN"})
    void authorizedRolesReadLatestAndRealPaginationShape(String role)
            throws Exception {
        var value = reading(Instant.now());
        when(readings.findLatest(101L)).thenReturn(Optional.of(value));
        when(readings.history(101L, 0, 1)).thenReturn(
                new PageImpl<>(List.of(value), PageRequest.of(0, 1), 3L));

        mvc.perform(get(ROOT + "/latest").with(user(EMAIL).roles(role)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value("ONLINE"))
                .andExpect(jsonPath("$.data.reading.deviceId").value(101))
                .andExpect(jsonPath("$.data.reading.simulated").value(true))
                .andExpect(jsonPath("$.data.reading.secret").doesNotExist())
                .andExpect(jsonPath("$.data.reading.secretHash").doesNotExist());

        mvc.perform(get(ROOT + "/history?page=0&size=1")
                        .with(user(EMAIL).roles(role)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].readingId").value(501))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false));

        var order = inOrder(policy, deviceQueries, readings);
        order.verify(policy).requireVerified(any(Authentication.class));
        order.verify(deviceQueries).get(101L, EMAIL);
        order.verify(readings).findLatest(101L);
        order.verify(policy).requireVerified(any(Authentication.class));
        order.verify(deviceQueries).get(101L, EMAIL);
        order.verify(readings).history(101L, 0, 1);
    }

    @Test
    void unverifiedSessionCannotReachDeviceOrTelemetryQueries() throws Exception {
        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Access verification is required"))
                .when(policy).requireVerified(any(Authentication.class));

        for (String suffix : new String[] {"/latest", "/history"}) {
            mvc.perform(get(ROOT + suffix).with(user(EMAIL).roles("USER")))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(deviceQueries, readings);
    }

    @Test
    void removedCustomerCannotReadDeviceTelemetry() throws Exception {
        when(deviceQueries.get(101L, EMAIL)).thenThrow(
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Device not found"));

        for (String suffix : new String[] {"/latest", "/history"}) {
            mvc.perform(get(ROOT + suffix).with(user(EMAIL).roles("USER")))
                    .andExpect(status().isNotFound());
        }
        verifyNoInteractions(readings);
    }

    @Test
    void distributorCannotReadOutsideAssignedDeviceScope() throws Exception {
        when(deviceQueries.get(101L, EMAIL)).thenThrow(
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Device not found"));

        for (String suffix : new String[] {"/latest", "/history"}) {
            mvc.perform(get(ROOT + suffix)
                            .with(user(EMAIL).roles("DISTRIBUTOR_ADMIN")))
                    .andExpect(status().isNotFound());
        }
        verifyNoInteractions(readings);
    }

    @Test
    void staleOrFutureReceptionTimeDoesNotAppearOnline() throws Exception {
        for (Instant received : new Instant[] {
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60)
        }) {
            when(readings.findLatest(101L))
                    .thenReturn(Optional.of(reading(received)));

            mvc.perform(get(ROOT + "/latest")
                            .with(user(EMAIL).roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("OFFLINE"))
                    .andExpect(jsonPath("$.data.reading.readingId").value(501));
        }
    }

    @Test
    void freshReadingReportsOnlineWithExplicitThreshold() throws Exception {
        when(readings.findLatest(101L))
                .thenReturn(Optional.of(reading(Instant.now())));

        mvc.perform(get(ROOT + "/latest").with(user(EMAIL).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ONLINE"))
                .andExpect(jsonPath("$.data.staleAfterSeconds").value(30))
                .andExpect(jsonPath("$.data.serverTime").isNotEmpty());
    }

    @Test
    void noReadingsReportsNoDataWithoutInventingMeasurements() throws Exception {
        when(readings.findLatest(101L)).thenReturn(Optional.empty());

        mvc.perform(get(ROOT + "/latest").with(user(EMAIL).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NO_DATA"))
                .andExpect(jsonPath("$.data.reading")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void invalidPaginationReturns400WithoutQueryingReadings() throws Exception {
        for (String parameters : new String[] {
                "?page=-1&size=20", "?page=0&size=0", "?page=0&size=101"
        }) {
            mvc.perform(get(ROOT + "/history" + parameters)
                            .with(user(EMAIL).roles("USER")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(readings);
    }

    @Test
    void malformedNumericParametersReturn400() throws Exception {
        for (String path : new String[] {
                ROOT + "/history?page=invalid",
                ROOT + "/history?size=invalid",
                "/api/v1/devices/invalid/telemetry/latest"
        }) {
            mvc.perform(get(path).with(user(EMAIL).roles("USER")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(policy, deviceQueries, readings);
    }

    @Test
    void databaseFailureIsAnErrorInsteadOfNoData() throws Exception {
        when(readings.findLatest(101L)).thenThrow(
                new DataAccessResourceFailureException(
                        "Simulated telemetry database failure"));

        mvc.perform(get(ROOT + "/latest").with(user(EMAIL).roles("USER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}