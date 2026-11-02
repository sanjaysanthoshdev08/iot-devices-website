package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.TelemetryRequest;
import com.hykon.iot_backend.entity.Device;
import com.hykon.iot_backend.repository.DeviceRepository;
import com.hykon.iot_backend.repository.DeviceTelemetryStore;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceTelemetryIngestionServiceTest {

    private static final ValidatorFactory FACTORY =
            Validation.buildDefaultValidatorFactory();

    private DeviceTelemetryAuthenticationService authentication;
    private DeviceRepository devices;
    private DeviceTelemetryStore store;
    private DeviceTelemetryIngestionService service;
    private Device device;
    private final UUID boot = UUID.randomUUID();
    private final String key = UUID.randomUUID().toString();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    @BeforeEach
    void setUp() {
        authentication = mock(DeviceTelemetryAuthenticationService.class);
        devices = mock(DeviceRepository.class);
        store = mock(DeviceTelemetryStore.class);
        device = mock(Device.class);

        service = new DeviceTelemetryIngestionService(
                authentication, devices, store, FACTORY.getValidator());

        when(authentication.authenticate(key, "test-secret"))
                .thenReturn(new DeviceTelemetryAuthenticationService.DeviceIdentity(
                        UUID.fromString(key), 101L));
        when(devices.findByIdForUpdate(101L))
                .thenReturn(Optional.of(device));
        when(device.getDeviceCode()).thenReturn("HALO1200-1");
        when(device.getStatus()).thenReturn("ACTIVE");
        when(store.insertSample(
                eq(101L), any(), anyString(), any(Instant.class)))
                .thenReturn(501L);
    }

    private TelemetryRequest sample(long sequence, long uptime, String code) {
        return new TelemetryRequest(
                1, code, boot, sequence, null, uptime,
                true, TelemetryRequest.OperatingMode.LINE,
                new TelemetryRequest.Battery(null, null, null),
                new TelemetryRequest.Grid(null, null, null, null),
                new TelemetryRequest.Load(null, null, null, null));
    }

    private TelemetryRequest sample() {
        return sample(1L, 5000L, "HALO1200-1");
    }

    private void rejects(TelemetryRequest request, HttpStatus status) {
        var error = assertThrows(ResponseStatusException.class,
                () -> service.ingest(key, "test-secret", request));
        assertEquals(status, error.getStatusCode());
        verify(store, never()).insertSample(
                anyLong(), any(), anyString(), any());
        verify(store, never()).insertBoot(anyLong(), any(), any());
        verify(store, never()).retireOtherBoots(anyLong(), any(), any());
    }

    @Test
    void replacementBootNeedsSynchronizedTimeAfterLastAcceptedReading() {
        UUID oldBoot = UUID.randomUUID();
        Instant previousReceived = Instant.now().minusSeconds(30);

        when(store.findActiveBoot(101L)).thenReturn(Optional.of(
                new DeviceTelemetryStore.Boot(
                        1L, oldBoot, previousReceived.minusSeconds(5), null)));
        when(store.findLastSample(101L, oldBoot)).thenReturn(Optional.of(
                new DeviceTelemetryStore.Sample(
                        400L, "a".repeat(64), 1L, 5000L,
                        previousReceived.minusSeconds(1), previousReceived)));

        rejects(sample(), HttpStatus.CONFLICT);

        var base = sample();
        var stale = new TelemetryRequest(
                base.schemaVersion(), base.deviceCode(), base.bootId(),
                base.sequence(), previousReceived, base.uptimeMs(),
                base.simulated(), base.operatingMode(),
                base.battery(), base.grid(), base.load());
        rejects(stale, HttpStatus.CONFLICT);

        var fresh = new TelemetryRequest(
                base.schemaVersion(), base.deviceCode(), base.bootId(),
                base.sequence(), previousReceived.plusSeconds(1),
                base.uptimeMs(), base.simulated(), base.operatingMode(),
                base.battery(), base.grid(), base.load());

        var receipt = service.ingest(key, "test-secret", fresh);
        assertFalse(receipt.duplicate());
        verify(store).insertBoot(101L, boot, receipt.receivedAt());
        verify(store).retireOtherBoots(101L, boot, receipt.receivedAt());
    }

    @Test
    void locksAndRechecksCredentialsBeforeWritingNewBootAndSample() {
        var request = sample();
        var receipt = service.ingest(key, "test-secret", request);

        assertEquals(501L, receipt.readingId());
        assertFalse(receipt.duplicate());
        assertEquals(boot, receipt.bootId());

        var order = inOrder(authentication, devices, store);
        order.verify(authentication).authenticate(key, "test-secret");
        order.verify(devices).findByIdForUpdate(101L);
        order.verify(authentication).authenticate(key, "test-secret");
        order.verify(store).findSample(101L, boot, 1L);
        order.verify(store).findBoot(101L, boot);
        order.verify(store).findActiveBoot(101L);
        order.verify(store).insertBoot(101L, boot, receipt.receivedAt());
        order.verify(store).insertSample(
                101L, request, TelemetryPayloadHasher.hash(request),
                receipt.receivedAt());
        order.verify(store).retireOtherBoots(
                101L, boot, receipt.receivedAt());
    }

    @Test
    void identicalRetryReturnsOriginalReceiptWithoutWritingOrRefreshingTime() {
        Instant original = Instant.parse("2026-01-01T00:00:00Z");
        when(store.findSample(101L, boot, 1L)).thenReturn(Optional.of(
                new DeviceTelemetryStore.Sample(
                        400L, TelemetryPayloadHasher.hash(sample()),
                        1L, 5000L, null, original)));

        var receipt = service.ingest(key, "test-secret", sample());

        assertTrue(receipt.duplicate());
        assertEquals(400L, receipt.readingId());
        assertEquals(original, receipt.receivedAt());
        verify(store, never()).insertSample(
                anyLong(), any(), anyString(), any());
        verify(store, never()).insertBoot(anyLong(), any(), any());
        verify(store, never()).retireOtherBoots(anyLong(), any(), any());
    }

    @Test
    void rejectsSameSampleIdentityWithDifferentContent() {
        when(store.findSample(101L, boot, 1L)).thenReturn(Optional.of(
                new DeviceTelemetryStore.Sample(
                        400L, "a".repeat(64), 1L, 5000L,
                        null, Instant.now())));

        rejects(sample(), HttpStatus.CONFLICT);
    }

    @Test
    void rejectsAnotherDeviceCode() {
        rejects(sample(1L, 5000L, "OTHER-DEVICE"), HttpStatus.FORBIDDEN);
        verifyNoInteractions(store);
    }

    @Test
    void rejectsInactiveDevice() {
        when(device.getStatus()).thenReturn("INACTIVE");

        rejects(sample(), HttpStatus.FORBIDDEN);
        verifyNoInteractions(store);
    }

    @Test
    void rejectsCredentialRevokedWhileWaitingForDeviceLock() {
        when(authentication.authenticate(key, "test-secret"))
                .thenReturn(new DeviceTelemetryAuthenticationService.DeviceIdentity(
                        UUID.fromString(key), 101L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid device credential"));

        rejects(sample(), HttpStatus.UNAUTHORIZED);
        verify(devices).findByIdForUpdate(101L);
        verifyNoInteractions(store);
    }

    @Test
    void rejectsNewSamplesFromRetiredBoot() {
        when(store.findBoot(101L, boot)).thenReturn(Optional.of(
                new DeviceTelemetryStore.Boot(
                        1L, boot, Instant.now().minusSeconds(20),
                        Instant.now().minusSeconds(10))));

        rejects(sample(), HttpStatus.CONFLICT);
    }

    @Test
    void requiresStartupSampleBeforeAcceptingUnknownBoot() {
        rejects(sample(2L, 10000L, "HALO1200-1"), HttpStatus.CONFLICT);
    }

    @Test
    void rejectsBackwardSequenceAndUptime() {
        when(store.findBoot(101L, boot)).thenReturn(Optional.of(
                new DeviceTelemetryStore.Boot(
                        1L, boot, Instant.now().minusSeconds(20), null)));
        when(store.findLastSample(101L, boot)).thenReturn(Optional.of(
                new DeviceTelemetryStore.Sample(
                        400L, "a".repeat(64), 5L, 25000L,
                        null, Instant.now())));

        rejects(sample(4L, 30000L, "HALO1200-1"), HttpStatus.CONFLICT);
        rejects(sample(6L, 24000L, "HALO1200-1"), HttpStatus.CONFLICT);
    }

    @Test
    void rejectsInvalidPayloadBeforeDeviceLookup() {
        rejects(sample(0L, -1L, ""), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(devices, store);
    }

    @Test
    void rejectsFutureAndOldTimestamps() {
        var base = sample();
        for (Instant measured : new Instant[] {
                Instant.now().plusSeconds(600),
                Instant.now().minusSeconds(90000)
        }) {
            var request = new TelemetryRequest(
                    base.schemaVersion(), base.deviceCode(), base.bootId(),
                    base.sequence(), measured, base.uptimeMs(),
                    base.simulated(), base.operatingMode(),
                    base.battery(), base.grid(), base.load());

            rejects(request, HttpStatus.BAD_REQUEST);
        }
    }
}