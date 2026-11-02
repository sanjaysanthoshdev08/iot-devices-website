package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.TelemetryRequest;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Hashes an already validated request in fixed contract order.
 * Length-prefixed fields distinguish null, empty text and field boundaries.
 * Numeric scale and JSON property order do not affect sample identity.
 */
public final class TelemetryPayloadHasher {

    private TelemetryPayloadHasher() {}

    public static String hash(TelemetryRequest request) {
        Objects.requireNonNull(request, "Telemetry request is required");

        var battery = Objects.requireNonNull(request.battery());
        var grid = Objects.requireNonNull(request.grid());
        var load = Objects.requireNonNull(request.load());

        Object[] fields = {
                "HYKON-TELEMETRY-V1",
                request.schemaVersion(), request.deviceCode(),
                request.bootId(), request.sequence(), request.measuredAt(),
                request.uptimeMs(), request.simulated(), request.operatingMode(),
                battery.voltageV(), battery.currentA(), battery.socPercent(),
                grid.present(), grid.voltageV(), grid.frequencyHz(),
                grid.inputPowerW(),
                load.voltageV(), load.currentA(), load.outputPowerW(),
                load.loadPercent()
        };

        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                for (Object field : fields) {
                    writeField(output, field);
                }
            }

            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(bytes.toByteArray())
            );
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Telemetry payload hashing failed", exception);
        }
    }

    private static void writeField(DataOutputStream output, Object value)
            throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }

        String canonical = value instanceof BigDecimal decimal
                ? decimal.stripTrailingZeros().toString()
                : value.toString();
        byte[] encoded = canonical.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}