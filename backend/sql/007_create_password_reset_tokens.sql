-- Migration 007: password reset tokens.
-- Additive: creates one new table; does not alter users, sessions or tokens.
-- Only a SHA-256 hash of each reset token is stored, never the token itself.
-- (Hibernate ddl-auto=update also creates this table from the entity; this
-- script is the reviewed, explicit equivalent.)

CREATE TABLE IF NOT EXISTS public.password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT LOCALTIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_id
    ON public.password_reset_tokens(user_id);
