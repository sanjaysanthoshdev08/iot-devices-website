-- Migration 005: device credentials and telemetry.
-- Additive: does not alter existing devices, users, ownership or sessions.
-- No credentials or readings are inserted by this migration.
-- Apply through a transaction only after isolated PostgreSQL tests and backup.

CREATE TABLE device_telemetry_credentials (
    key_id UUID PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    secret_hash VARCHAR(64) NOT NULL,
    created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_telemetry_secret_hash
        CHECK (secret_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_telemetry_credential_dates
        CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX idx_telemetry_credentials_device
    ON device_telemetry_credentials(device_id);

CREATE UNIQUE INDEX uq_telemetry_active_device_credential
    ON device_telemetry_credentials(device_id)
    WHERE revoked_at IS NULL;

-- Track each startup separately so sequence resets are not confused with
-- retries. first_received_at and id are assigned by the backend/database.
CREATE TABLE device_telemetry_boots (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    boot_id UUID NOT NULL,
    first_received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    retired_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_telemetry_device_boot UNIQUE (device_id, boot_id),
    CONSTRAINT ck_telemetry_boot_dates
        CHECK (retired_at IS NULL OR retired_at >= first_received_at)
);

CREATE INDEX idx_telemetry_boots_device_order
    ON device_telemetry_boots(device_id, id DESC);

CREATE TABLE device_telemetry_readings (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    boot_id UUID NOT NULL,
    sequence BIGINT NOT NULL,
    schema_version INTEGER NOT NULL,
    measured_at TIMESTAMP WITH TIME ZONE,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    uptime_ms BIGINT NOT NULL,
    simulated BOOLEAN NOT NULL,
    operating_mode VARCHAR(20) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,

    battery_voltage_v NUMERIC,
    battery_current_a NUMERIC,
    battery_soc_percent NUMERIC,

    grid_present BOOLEAN,
    grid_voltage_v NUMERIC,
    grid_frequency_hz NUMERIC,
    grid_input_power_w NUMERIC,

    load_voltage_v NUMERIC,
    load_current_a NUMERIC,
    load_output_power_w NUMERIC,
    load_percent NUMERIC,

    CONSTRAINT fk_telemetry_reading_boot
        FOREIGN KEY (device_id, boot_id)
        REFERENCES device_telemetry_boots(device_id, boot_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_telemetry_sample
        UNIQUE (device_id, boot_id, sequence),
    CONSTRAINT ck_telemetry_sequence CHECK (sequence > 0),
    CONSTRAINT ck_telemetry_schema CHECK (schema_version = 1),
    CONSTRAINT ck_telemetry_uptime CHECK (uptime_ms >= 0),
    CONSTRAINT ck_telemetry_mode CHECK (
        operating_mode IN (
            'LINE', 'BATTERY', 'BYPASS', 'STANDBY', 'FAULT', 'UNKNOWN'
        )
    ),
    CONSTRAINT ck_telemetry_payload_hash
        CHECK (payload_hash ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_telemetry_battery_voltage CHECK (
        battery_voltage_v IS NULL OR (
            battery_voltage_v >= 0 AND
            battery_voltage_v NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
        )
    ),
    CONSTRAINT ck_telemetry_battery_current CHECK (
        battery_current_a IS NULL OR
        battery_current_a NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
    ),
    CONSTRAINT ck_telemetry_battery_soc CHECK (
        battery_soc_percent IS NULL OR
        battery_soc_percent BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_telemetry_grid_voltage CHECK (
        grid_voltage_v IS NULL OR (
            grid_voltage_v >= 0 AND
            grid_voltage_v NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
        )
    ),
    CONSTRAINT ck_telemetry_grid_frequency CHECK (
        grid_frequency_hz IS NULL OR (
            grid_frequency_hz >= 0 AND
            grid_frequency_hz NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
        )
    ),
    CONSTRAINT ck_telemetry_grid_power CHECK (
        grid_input_power_w IS NULL OR
        grid_input_power_w NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
    ),
    CONSTRAINT ck_telemetry_load_voltage CHECK (
        load_voltage_v IS NULL OR (
            load_voltage_v >= 0 AND
            load_voltage_v NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
        )
    ),
    CONSTRAINT ck_telemetry_load_current CHECK (
        load_current_a IS NULL OR (
            load_current_a >= 0 AND
            load_current_a NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
        )
    ),
    CONSTRAINT ck_telemetry_load_power CHECK (
        load_output_power_w IS NULL OR (
            load_output_power_w >= 0 AND
            load_output_power_w NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
        )
    ),
    CONSTRAINT ck_telemetry_load_percent CHECK (
        load_percent IS NULL OR (
            load_percent >= 0 AND
            load_percent NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
        )
    )
);

CREATE INDEX idx_telemetry_readings_history
    ON device_telemetry_readings(device_id, received_at DESC, id DESC);

-- uq_telemetry_sample also supports per-boot sequence lookups.
-- Retry comparisons use a canonical payload hash computed by the backend.
-- Revoking/rotating a device credential does not delete measurement history.
-- Intentionally no IF NOT EXISTS: unexpected existing tables must be reviewed,
-- rather than silently accepting an incompatible schema.