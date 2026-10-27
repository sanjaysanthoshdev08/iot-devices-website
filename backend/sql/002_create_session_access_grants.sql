BEGIN;

DO $guard$
BEGIN
    IF current_database() <> 'hykon_iot' THEN
        RAISE EXCEPTION 'Wrong database. Access grant setup cancelled.';
    END IF;
END;
$guard$;

CREATE TABLE public.session_access_grants (
    session_id UUID PRIMARY KEY,
    device_id BIGINT,
    branch_id BIGINT,
    device_verified_at TIMESTAMP WITHOUT TIME ZONE,
    distributor_verified_at TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT fk_access_grants_session
        FOREIGN KEY (session_id)
        REFERENCES public.login_sessions(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_access_grants_device
        FOREIGN KEY (device_id)
        REFERENCES public.devices(id),

    CONSTRAINT fk_access_grants_branch
        FOREIGN KEY (branch_id)
        REFERENCES public.distributor_branches(id),

    CONSTRAINT chk_access_grants_device_pair
        CHECK (
            (device_id IS NULL) = (device_verified_at IS NULL)
        ),

    CONSTRAINT chk_access_grants_branch_pair
        CHECK (
            (branch_id IS NULL) = (distributor_verified_at IS NULL)
        ),

    CONSTRAINT chk_access_grants_has_verification
        CHECK (device_id IS NOT NULL OR branch_id IS NOT NULL)
);

CREATE INDEX idx_access_grants_device_id
    ON public.session_access_grants(device_id);

CREATE INDEX idx_access_grants_branch_id
    ON public.session_access_grants(branch_id);

COMMIT;

SELECT COUNT(*) AS access_grant_count
FROM public.session_access_grants;