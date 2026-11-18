-- Migration 014: universal (v2) telemetry extras.
-- Additive: existing readings, columns and constraints are not changed.
-- Stores measurements a device reports that have no dedicated column yet
-- (for example another inverter model), plus firmware version and device model.

ALTER TABLE device_telemetry_readings
    ADD COLUMN IF NOT EXISTS extra_measurements JSONB,
    ADD COLUMN IF NOT EXISTS firmware_version VARCHAR(40),
    ADD COLUMN IF NOT EXISTS device_model VARCHAR(60);
