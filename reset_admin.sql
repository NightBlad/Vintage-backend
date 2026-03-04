-- Reset admin account script
-- First, delete existing admin user
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username = 'admin');
DELETE FROM users WHERE username = 'admin';

-- Create fresh admin user with valid phone number
INSERT INTO users (username, email, password, full_name, phone, address, enabled, failed_attempts, account_locked, created_at, updated_at)
VALUES (
    'admin',
    'admin@vintage-pharmacy.com',
    '$2a$10$BNKL6.YZrw3xK7DsYJ9mXuy/K9XOHfCH.J5x8UfrUuPb8VZwF8z8u', -- admin123 encoded
    'Quản trị viên hệ thống',
    '0123456789',
    '123 Đường ABC, TP.HCM',
    true,
    0,
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- Assign admin role
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'ROLE_ADMIN';
