package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.TelemetryV2Request;
import com.hykon.iot_backend.repository.DeviceTelemetryStore;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceTelemetryV2IngestionServiceTest {

    private final DeviceTelemetryIngestionService core = mock(DeviceTelemetryIngestionService.class);
    private final DeviceTelemetryStore store = mock(DeviceTelemetryStore.class);
    private final DeviceTelemetryV2IngestionService service = new DeviceTelemetryV2IngestionService(
            core, store, Validation.buildDefaultValidatorFactory().getValidator());

    private static TelemetryV2Request request() {
        return new TelemetryV2Request(2, "dev-1", UUID.randomUUID(), 2L, null, 1000L, false, null,
                "1.0", "M1", List.of(
                        new TelemetryV2Request.Measurement("battery_voltage_v", new BigDecimal("52"), "V"),
                        new TelemetryV2Request.Measurement("pv_power_w", new BigDecimal("1200"), "W")));
    }

    private static DeviceTelemetryIngestionService.Receipt receipt(boolean duplicate) {
        return new DeviceTelemetryIngestionService.Receipt(
                7L, UUID.randomUUID(), 2L, Instant.now(), duplicate);
    }

    @Test
    void newReadingStoresExtrasFirmwareAndModel() {
        when(core.ingest(anyString(), anyString(), any())).thenReturn(receipt(false));
        service.ingest("k", "s", request());
        verify(store).attachExtras(7L, "{\"pv_power_w\":{\"value\":1200,\"unit\":\"W\"}}", "1.0", "M1");
    }

    @Test
    void identicalRetryIsAcknowledgedWithoutWriting() {
        when(core.ingest(anyString(), anyString(), any())).thenReturn(receipt(true));
        when(store.extrasMatch(7L, "{\"pv_power_w\":{\"value\":1200,\"unit\":\"W\"}}")).thenReturn(true);
        var result = service.ingest("k", "s", request());
        assertEquals(true, result.duplicate());
        verify(store, never()).attachExtras(any(Long.class), any(), any(), any());
    }

    @Test
    void retryWithChangedExtrasIsAConflict() {
        when(core.ingest(anyString(), anyString(), any())).thenReturn(receipt(true));
        when(store.extrasMatch(any(Long.class), any())).thenReturn(false);
        var ex = assertThrows(ResponseStatusException.class, () -> service.ingest("k", "s", request()));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void invalidPayloadIsRejectedBeforeTheCoreIsCalled() {
        var bad = new TelemetryV2Request(2, "dev-1", UUID.randomUUID(), 2L, null, 1000L, false, null,
                null, null, List.of());
        var ex = assertThrows(ResponseStatusException.class, () -> service.ingest("k", "s", bad));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(core, never()).ingest(anyString(), anyString(), any());
    }
}
