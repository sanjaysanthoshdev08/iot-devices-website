-- Migration 010: branch pincode.
-- Additive: one nullable column; existing branches and code keep working.
-- (Hibernate ddl-auto=update also adds this column from the entity; this
-- script is the reviewed, explicit equivalent.)

ALTER TABLE public.distributor_branches ADD COLUMN IF NOT EXISTS pincode VARCHAR(10);
