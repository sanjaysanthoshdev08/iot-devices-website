-- One Distributor Admin login oversees every distributor organization,
-- so the one-distributor-per-administrator restriction is removed.
ALTER TABLE distributors DROP CONSTRAINT IF EXISTS uk_distributors_administrator;
