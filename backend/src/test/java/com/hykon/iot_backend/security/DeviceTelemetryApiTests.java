package com.hykon.iot_backend.security;

import com.hykon.iot_backend.config.*;
import com.hykon.iot_backend.controller.DeviceTelemetryController;
import com.hykon.iot_backend.dto.TelemetryRequest;
import com.hykon.iot_backend.entity.Device;
import com.hykon.iot_backend.exception.GlobalExceptionHandler;
import com.hykon.iot_backend.repository.*;
import com.hykon.iot_backend.service.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DeviceTelemetryController.class)
@Import({
        SecurityConfig.class,
        DeviceTelemetrySecurityConfig.class,
        GlobalExceptionHandler.class,
        DeviceTelemetryAuthenticationService.class,
        DeviceTelemetryIngestionService.class,
        SecurityAndValidationTests.NoDefaultUserConfiguration.class
})
class DeviceTelemetryApiTests {

    private static final String PATH = "/api/v1/telemetry/readings";
    private static final UUID KEY =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BOOT =
            UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static final String BODY = """
            {
              "schemaVersion": 1,
              "deviceCode": "HALO1200-1",
              "bootId": "22222222-2222-4222-8222-222222222222",
              "sequence": 1,
              "measuredAt": null,
              "uptimeMs": 5000,
              "simulated": true,
              "operatingMode": "LINE",
              "battery": {
                "voltageV": null, "currentA": null, "socPercent": null
              },
              "grid": {
                "present": null, "voltageV": null,
                "frequencyHz": null, "inputPowerW": null
              },
              "load": {
                "voltageV": null, "currentA": null,
                "outputPowerW": null, "loadPercent": null
              }
            }
            """;

    @Autowired private MockMvc mvc;

    @MockitoBean private JwtService jwtService;
    @MockitoBean private LoginSessionService loginSessions;
    @MockitoBean private UserRepository users;
    @MockitoBean private DeviceRepository devices;
    @MockitoBean private DeviceTelemetryCredentialStore credentials;
    @MockitoBean private DeviceTelemetryStore store;

    private String secret;
    private Device device;

    @BeforeEach
    void setUp() {
        secret = DeviceSecretCodec.generate();
        device = mock(Device.class);

        when(credentials.findByKeyId(KEY)).thenReturn(Optional.of(
                new DeviceTelemetryCredentialStore.StoredCredential(
                        KEY, 101L, DeviceSecretCodec.hash(secret), null)));
        when(devices.findByIdForUpdate(101L))
                .thenReturn(Optional.of(device));
        when(device.getDeviceCode()).thenReturn("HALO1200-1");
        when(device.getStatus()).thenReturn("ACTIVE");
        when(store.insertSample(
                eq(101L), any(), anyString(), any(Instant.class)))
                .thenReturn(501L);
    }

    private MockHttpServletRequestBuilder upload(String body) {
        return post(PATH)
                .header("X-Device-Key-Id", KEY.toString())
                .header("X-Device-Secret", secret)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private TelemetryRequest sampleRequest() {
        return new TelemetryRequest(
                1, "HALO1200-1", BOOT, 1L, null, 5000L,
                true, TelemetryRequest.OperatingMode.LINE,
                new TelemetryRequest.Battery(null, null, null),
                new TelemetryRequest.Grid(null, null, null, null),
                new TelemetryRequest.Load(null, null, null, null));
    }

    @Test
    void acceptsDeviceCredentialWithoutWebsiteJwtAndDoesNotReturnSecrets()
            throws Exception {
        mvc.perform(upload(BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.readingId").value(501))
                .andExpect(jsonPath("$.data.duplicate").value(false))
                .andExpect(content().string(not(containsString(secret))))
                .andExpect(content().string(not(
                        containsString(DeviceSecretCodec.hash(secret)))));

        verify(credentials, times(2)).findByKeyId(KEY);
        verify(devices).findByIdForUpdate(101L);
        verifyNoInteractions(jwtService, loginSessions, users);
    }

    @Test
    void rejectsMissingDeviceHeaders() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("X-Device-Key-Id", KEY.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("X-Device-Secret", secret)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(credentials, devices, store);
    }

    @Test
    void websiteLoginOrBearerTokenAloneCannotUpload() throws Exception {
        for (String role : new String[] {
                "USER", "DISTRIBUTOR_ADMIN", "HYKON_ADMIN"
        }) {
            mvc.perform(post(PATH).with(user("website-user").roles(role))
                            .header("Authorization", "Bearer website-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(credentials, devices, store, jwtService);
    }

    @Test
    void rejectsWrongUnknownAndRevokedCredentials() throws Exception {
        mvc.perform(post(PATH)
                        .header("X-Device-Key-Id", KEY.toString())
                        .header("X-Device-Secret", DeviceSecretCodec.generate())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        when(credentials.findByKeyId(KEY)).thenReturn(Optional.empty());
        mvc.perform(upload(BODY)).andExpect(status().isUnauthorized());

        when(credentials.findByKeyId(KEY)).thenReturn(Optional.of(
                new DeviceTelemetryCredentialStore.StoredCredential(
                        KEY, 101L, DeviceSecretCodec.hash(secret), Instant.now())));
        mvc.perform(upload(BODY)).andExpect(status().isUnauthorized());

        verifyNoInteractions(devices, store);
    }

    @Test
    void rejectsAmbiguousRepeatedCredentialHeaders() throws Exception {
        mvc.perform(upload(BODY)
                        .header("X-Device-Key-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(upload(BODY)
                        .header("X-Device-Secret", DeviceSecretCodec.generate()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(credentials, devices, store);
    }

    @Test
    void malformedJsonAndInvalidTypesReturn400() throws Exception {
        for (String body : new String[] {
                "{",
                BODY.replace(BOOT.toString(), "invalid-uuid"),
                BODY.replace("\"LINE\"", "\"INVALID_MODE\""),
                BODY.replace("\"uptimeMs\": 5000", "\"uptimeMs\": \"invalid\"")
        }) {
            mvc.perform(upload(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(content().string(not(containsString(secret))));
        }
        verifyNoInteractions(credentials, devices, store);
    }

    @Test
    void rejectsInvalidMeasurementValidationBeforeWriting() throws Exception {
        mvc.perform(upload(BODY.replace(
                        "\"socPercent\": null", "\"socPercent\": 101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['battery.socPercent']").exists());

        verifyNoInteractions(credentials, devices, store);
    }

    @Test
    void unchangedRetryReturnsOriginalReceipt() throws Exception {
        Instant original = Instant.parse("2026-01-01T00:00:00Z");
        when(store.findSample(101L, BOOT, 1L)).thenReturn(Optional.of(
                new DeviceTelemetryStore.Sample(
                        400L, TelemetryPayloadHasher.hash(sampleRequest()),
                        1L, 5000L, null, original)));

        mvc.perform(upload(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readingId").value(400))
                .andExpect(jsonPath("$.data.duplicate").value(true))
                .andExpect(jsonPath("$.data.receivedAt")
                        .value("2026-01-01T00:00:00Z"));

        verify(store, never()).insertSample(
                anyLong(), any(), anyString(), any());
    }

    @Test
    void changedRetryReturnsConflict() throws Exception {
        when(store.findSample(101L, BOOT, 1L)).thenReturn(Optional.of(
                new DeviceTelemetryStore.Sample(
                        400L, "a".repeat(64), 1L, 5000L,
                        null, Instant.now())));

        mvc.perform(upload(BODY))
                .andExpect(status().isConflict());
        verify(store, never()).insertSample(
                anyLong(), any(), anyString(), any());
    }

    @Test
    void deviceHeadersCannotAuthorizeManagementOperations() throws Exception {
        mvc.perform(post(
                        "/api/v1/management/devices/101/telemetry-credential")
                        .header("X-Device-Key-Id", KEY.toString())
                        .header("X-Device-Secret", secret))
                .andExpect(status().isUnauthorized());

        mvc.perform(post(
                        "/api/v1/management/devices/101/telemetry-credential")
                        .with(user("customer").roles("USER"))
                        .header("X-Device-Key-Id", KEY.toString())
                        .header("X-Device-Secret", secret))
                .andExpect(status().isForbidden());

        verifyNoInteractions(credentials, devices, store);
    }

    @Test
    void deviceHeadersCannotAuthorizeWebsiteDeviceQueries() throws Exception {
        mvc.perform(get("/api/v1/devices/101")
                        .header("X-Device-Key-Id", KEY.toString())
                        .header("X-Device-Secret", secret))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(credentials, devices, store);
    }

    @Test
    void telemetryPathDeniesOtherMethodsEvenForWebsiteAdministrators()
            throws Exception {
        for (String method : new String[] {"GET", "PUT", "DELETE"}) {
            mvc.perform(request(
                            org.springframework.http.HttpMethod.valueOf(method),
                            PATH)
                            .with(user("admin").roles("HYKON_ADMIN"))
                            .header("X-Device-Key-Id", KEY.toString())
                            .header("X-Device-Secret", secret))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(credentials, devices, store);
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mvc.perform(post(PATH)
                        .header("X-Device-Key-Id", KEY.toString())
                        .header("X-Device-Secret", secret)
                        .contentType(MediaType.TEXT_PLAIN).content(BODY))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(credentials, devices, store);
    }

    @Test
    void inactiveDeviceCannotUpload() throws Exception {
        when(device.getStatus()).thenReturn("INACTIVE");

        mvc.perform(upload(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(store);
    }
}