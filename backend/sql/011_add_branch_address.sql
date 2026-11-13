-- Full street address for a branch (Location stays as the short verification value).
ALTER TABLE distributor_branches ADD COLUMN IF NOT EXISTS address VARCHAR(300);
