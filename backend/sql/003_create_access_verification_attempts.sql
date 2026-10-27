BEGIN;

DO $guard$
BEGIN
    IF current_database() <> 'hykon_iot' THEN
        RAISE EXCEPTION 'Wrong database. Attempt-limit setup cancelled.';
    END IF;
END;
$guard$;

CREATE TABLE public.access_verification_attempts (
    user_id BIGINT PRIMARY KEY,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
        DEFAULT LOCALTIMESTAMP,

    CONSTRAINT fk_verification_attempts_user
        FOREIGN KEY (user_id)
        REFERENCES public.users(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_verification_attempts_nonnegative
        CHECK (failed_attempts >= 0),

    CONSTRAINT chk_verification_attempts_lock
        CHECK (locked_until IS NULL OR failed_attempts > 0)
);

COMMIT;

SELECT COUNT(*) AS verification_attempt_record_count
FROM public.access_verification_attempts;