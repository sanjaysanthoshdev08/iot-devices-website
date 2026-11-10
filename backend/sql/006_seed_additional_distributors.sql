-- Seed: 006_seed_additional_distributors.sql
-- Description: Local development seed data. Adds two more distributors (DIST-B, DIST-C), each with
--              one branch and one registered, purchased and claimed system with a primary-owner customer.
--              Together with the existing DIST-A this gives 3 distributors on the dashboard Accounts page.
--              Data-only: no schema changes. Safe to re-run (every insert is skipped if the row exists).
--
-- Serial number format: YYMM + 5-digit running number, e.g. 261100001 (Nov 2026, device 1).
--
-- Test login password for all four accounts created here: HykonTest@2026
--   distributor_b_admin@example.com  (DISTRIBUTOR_ADMIN of DIST-B)
--   distributor_c_admin@example.com  (DISTRIBUTOR_ADMIN of DIST-C)
--   customer_b@example.com           (USER, owner of HALO1200-2)
--   customer_c@example.com           (USER, owner of HALO1200-3)
--
-- Run:  psql -U postgres -h localhost -d hykon_iot -f backend/sql/006_seed_additional_distributors.sql

BEGIN;

-- 1. Users (bcrypt hash of the test password above, same encoder the backend uses)
INSERT INTO users (name, email, password, status, role_id, created_at, updated_at)
SELECT v.name, v.email, '$2a$10$ZB2b5LiMOCSFEYkVLjFiNOU06fImyYHhjsVUVPHZKPFmKgM4X9a3i', 'ACTIVE',
       (SELECT id FROM roles WHERE name = v.role), NOW(), NOW()
FROM (VALUES
    ('Distributor B Administrator', 'distributor_b_admin@example.com', 'DISTRIBUTOR_ADMIN'),
    ('Distributor C Administrator', 'distributor_c_admin@example.com', 'DISTRIBUTOR_ADMIN'),
    ('Customer B',                  'customer_b@example.com',          'USER'),
    ('Customer C',                  'customer_c@example.com',          'USER')
) AS v(name, email, role)
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.email = v.email)
ORDER BY v.email;

-- 2. Distributors (created by the HYKON_ADMIN account)
INSERT INTO distributors (distributor_code, name, administrator_user_id, created_by_user_id, status, created_at, updated_at)
SELECT v.code, v.name,
       (SELECT id FROM users WHERE email = v.admin_email),
       (SELECT u.id FROM users u JOIN roles r ON r.id = u.role_id WHERE r.name = 'HYKON_ADMIN' ORDER BY u.id LIMIT 1),
       'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('DIST-B', 'DISTRIBUTOR B', 'distributor_b_admin@example.com'),
    ('DIST-C', 'DISTRIBUTOR C', 'distributor_c_admin@example.com')
) AS v(code, name, admin_email)
WHERE NOT EXISTS (SELECT 1 FROM distributors d WHERE d.distributor_code = v.code)
ORDER BY v.code;

-- 3. One branch per distributor
INSERT INTO distributor_branches (distributor_id, branch_code, name, location, status, created_at, updated_at)
SELECT d.id, 'MAIN', v.branch_name, v.location, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('DIST-B', 'THRISSUR',  'TCR'),
    ('DIST-C', 'KOZHIKODE', 'KKD')
) AS v(dist_code, branch_name, location)
JOIN distributors d ON d.distributor_code = v.dist_code
WHERE NOT EXISTS (SELECT 1 FROM distributor_branches b WHERE b.distributor_id = d.id AND b.branch_code = 'MAIN')
ORDER BY v.dist_code;

-- 4. One system per branch, owned by its customer
INSERT INTO devices (device_code, serial_number, system_name, model, branch_id, owner_user_id, created_by_user_id, status, created_at, updated_at)
SELECT v.device_code, v.serial, v.system_name, 'HALO 1200', b.id,
       (SELECT id FROM users WHERE email = v.owner_email),
       d.created_by_user_id, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('HALO1200-2', '261100002', 'Test HALO 1200 B', 'DIST-B', 'customer_b@example.com'),
    ('HALO1200-3', '261100003', 'Test HALO 1200 C', 'DIST-C', 'customer_c@example.com')
) AS v(device_code, serial, system_name, dist_code, owner_email)
JOIN distributors d ON d.distributor_code = v.dist_code
JOIN distributor_branches b ON b.distributor_id = d.id AND b.branch_code = 'MAIN'
WHERE NOT EXISTS (SELECT 1 FROM devices x WHERE x.device_code = v.device_code)
ORDER BY v.device_code;

-- 5. Purchase records, already claimed (mirrors what the claim flow leaves behind)
INSERT INTO device_purchases (device_id, customer_user_id, recorded_by_user_id, purchase_reference, status, claimed_at, created_at, updated_at)
SELECT dv.id, dv.owner_user_id, dv.created_by_user_id, v.ref, 'CLAIMED', NOW(), NOW(), NOW()
FROM (VALUES
    ('HALO1200-2', 'TEST-INV-002'),
    ('HALO1200-3', 'TEST-INV-003')
) AS v(device_code, ref)
JOIN devices dv ON dv.device_code = v.device_code
ON CONFLICT (device_id) DO NOTHING;

-- 6. Primary-owner access rows (what the dashboard lists as the device's users)
INSERT INTO device_authorized_customers (device_id, user_id, is_primary_owner, created_by_user_id, created_at)
SELECT dv.id, dv.owner_user_id, TRUE, dv.owner_user_id, NOW()
FROM devices dv
WHERE dv.device_code IN ('HALO1200-2', 'HALO1200-3') AND dv.owner_user_id IS NOT NULL
ON CONFLICT (device_id, user_id) DO NOTHING;

COMMIT;

-- Rollback (removes only what this seed added):
-- BEGIN;
-- DELETE FROM device_authorized_customers WHERE device_id IN (SELECT id FROM devices WHERE device_code IN ('HALO1200-2','HALO1200-3'));
-- DELETE FROM device_purchases WHERE device_id IN (SELECT id FROM devices WHERE device_code IN ('HALO1200-2','HALO1200-3'));
-- DELETE FROM devices WHERE device_code IN ('HALO1200-2','HALO1200-3');
-- DELETE FROM distributor_branches WHERE distributor_id IN (SELECT id FROM distributors WHERE distributor_code IN ('DIST-B','DIST-C'));
-- DELETE FROM distributors WHERE distributor_code IN ('DIST-B','DIST-C');
-- DELETE FROM users WHERE email IN ('distributor_b_admin@example.com','distributor_c_admin@example.com','customer_b@example.com','customer_c@example.com');
-- COMMIT;
