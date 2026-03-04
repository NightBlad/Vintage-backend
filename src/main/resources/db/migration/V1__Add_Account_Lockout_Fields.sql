-- Add columns for account lockout functionality and update existing users
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_attempts INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS account_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS lock_time TIMESTAMP;

-- Update existing users to have default values
UPDATE users SET failed_attempts = 0 WHERE failed_attempts IS NULL;
UPDATE users SET account_locked = FALSE WHERE account_locked IS NULL;
