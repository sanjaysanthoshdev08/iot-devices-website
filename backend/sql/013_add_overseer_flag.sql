-- distributor_admin@gmail.com is the one overseeing Distributor Admin (sees every distributor).
-- All other Distributor Admin logins are limited to the distributor(s) they administer.
ALTER TABLE users ADD COLUMN IF NOT EXISTS all_distributors boolean NOT NULL DEFAULT false;
UPDATE users SET all_distributors = true WHERE email = 'distributor_admin@gmail.com';
