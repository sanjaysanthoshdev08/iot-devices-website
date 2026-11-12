-- Migration 008: state and district for distributors.
-- Additive: two nullable columns; existing rows and code keep working.
-- (Hibernate ddl-auto=update also adds these columns from the entity; this
-- script is the reviewed, explicit equivalent.)

ALTER TABLE public.distributors ADD COLUMN IF NOT EXISTS state VARCHAR(100);
ALTER TABLE public.distributors ADD COLUMN IF NOT EXISTS district VARCHAR(100);
