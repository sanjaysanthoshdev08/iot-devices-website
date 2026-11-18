#!/bin/sh
# One-time database setup that Hibernate does not create by itself.
# Runs after the backend has created its tables. Safe to run again: it skips what already exists.
set -e
export PGPASSWORD="$POSTGRES_PASSWORD"
PSQL="psql -h db -U $POSTGRES_USER -d $POSTGRES_DB -v ON_ERROR_STOP=1"

until $PSQL -tAc "select to_regclass('public.devices')" | grep -q devices; do
  echo "Waiting for the backend to create its tables..."
  sleep 3
done

if [ "$($PSQL -tAc "select to_regclass('public.device_telemetry_credentials')")" = "" ]; then
  echo "Applying migration 005 (device credentials and telemetry)..."
  $PSQL -f /sql/005_create_device_telemetry.sql
else
  echo "Migration 005 already applied."
fi
echo "Database ready."
