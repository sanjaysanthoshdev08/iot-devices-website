BEGIN;

DO $guard$
BEGIN
    IF current_database() <> 'hykon_iot' THEN
        RAISE EXCEPTION 'Wrong database. Session setup cancelled.';
    END IF;
END;
$guard$;

CREATE TABLE public.login_sessions (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
        DEFAULT LOCALTIMESTAMP,
    CONSTRAINT fk_login_sessions_user
        FOREIGN KEY (user_id) REFERENCES public.users(id)
);

CREATE INDEX idx_login_sessions_user_id
    ON public.login_sessions(user_id);

CREATE INDEX idx_login_sessions_expires_at
    ON public.login_sessions(expires_at);

ALTER TABLE public.refresh_tokens
    ADD COLUMN session_id UUID;

ALTER TABLE public.refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_session
    FOREIGN KEY (session_id) REFERENCES public.login_sessions(id);

CREATE INDEX idx_refresh_tokens_session_id
    ON public.refresh_tokens(session_id);

COMMIT;

SELECT COUNT(*) AS login_session_count
FROM public.login_sessions;