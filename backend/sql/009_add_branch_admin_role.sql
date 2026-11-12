-- Migration 009: BRANCH_ADMIN role and branch -> admin link.
-- Additive: widens the role-name check, adds one role row and one nullable
-- unique column. Existing roles, users and branches are unchanged.

ALTER TABLE public.roles DROP CONSTRAINT IF EXISTS roles_name_check;
ALTER TABLE public.roles ADD CONSTRAINT roles_name_check
    CHECK (name IN ('USER', 'DISTRIBUTOR_ADMIN', 'HYKON_ADMIN', 'BRANCH_ADMIN'));

INSERT INTO public.roles (name)
SELECT 'BRANCH_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM public.roles WHERE name = 'BRANCH_ADMIN');

ALTER TABLE public.distributor_branches
    ADD COLUMN IF NOT EXISTS branch_admin_user_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_branches_admin') THEN
        ALTER TABLE public.distributor_branches
            ADD CONSTRAINT fk_branches_admin FOREIGN KEY (branch_admin_user_id)
            REFERENCES public.users(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_branches_admin') THEN
        ALTER TABLE public.distributor_branches
            ADD CONSTRAINT uk_branches_admin UNIQUE (branch_admin_user_id);
    END IF;
END $$;
