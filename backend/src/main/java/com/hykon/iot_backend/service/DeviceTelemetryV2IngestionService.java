package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.TelemetryV2Request;
import com.hykon.iot_backend.repository.DeviceTelemetryStore;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Universal telemetry intake. It converts the v2 payload to the existing reading and reuses the
 * proven ingestion path (device credentials, boot/sequence checks, duplicate detection), then
 * saves any extra measurements next to the same reading.
 */
@Service
@Transactional
public class DeviceTelemetryV2IngestionService {

    private final DeviceTelemetryIngestionService ingestion;
    private final DeviceTelemetryStore store;
    private final Validator validator;

    public DeviceTelemetryV2IngestionService(
            DeviceTelemetryIngestionService ingestion,
            DeviceTelemetryStore store,
            Validator validator
    ) {
        this.ingestion = ingestion;
        this.store = store;
        this.validator = validator;
    }

    public DeviceTelemetryIngestionService.Receipt ingest(
            String keyId, String secret, TelemetryV2Request request
    ) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid telemetry payload");
        }

        TelemetryV2Translator.Translation translation;
        try {
            translation = TelemetryV2Translator.translate(request);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        var receipt = ingestion.ingest(keyId, secret, translation.reading());

        if (!receipt.duplicate()) {
            store.attachExtras(
                    receipt.readingId(), translation.extrasJson(),
                    request.firmwareVersion(), request.model());
        }
        return receipt;
    }
}
