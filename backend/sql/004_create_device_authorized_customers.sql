-- Migration: 004_create_device_authorized_customers.sql
-- Description: Create device_authorized_customers table to support multiple authorized customer users per device (up to 3).
--              Backfills existing single-owner devices into this table with is_primary_owner = TRUE.

CREATE TABLE IF NOT EXISTS device_authorized_customers (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_primary_owner BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_device_customer UNIQUE (device_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_device_authorized_customers_user_id
    ON device_authorized_customers(user_id);

CREATE INDEX IF NOT EXISTS idx_device_authorized_customers_device_id
    ON device_authorized_customers(device_id);

-- Idempotent backfill: populate from existing devices.owner_user_id
INSERT INTO device_authorized_customers (device_id, user_id, is_primary_owner, created_by_user_id, created_at)
SELECT d.id, d.owner_user_id, TRUE, d.owner_user_id, CURRENT_TIMESTAMP
FROM devices d
WHERE d.owner_user_id IS NOT NULL
ON CONFLICT (device_id, user_id) DO UPDATE
SET is_primary_owner = TRUE;

-- Rollback instructions:
-- DROP TABLE IF EXISTS device_authorized_customers;
