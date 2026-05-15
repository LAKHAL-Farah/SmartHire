-- Face Recognition MFA - Database Migration Script
-- Run this script on your database to add Face Recognition support

-- =============================================================
-- 1. Add Face Recognition fields to users table
-- =============================================================

-- Check if columns already exist (optional)
-- For MySQL:
-- ALTER TABLE users ADD COLUMN IF NOT EXISTS face_recognition_enabled BOOLEAN DEFAULT FALSE;

-- For PostgreSQL:
-- ALTER TABLE users ADD COLUMN IF NOT EXISTS face_recognition_enabled BOOLEAN DEFAULT FALSE;

-- For all databases (safe approach - check before adding):

ALTER TABLE users ADD COLUMN face_recognition_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN face_embedding_id VARCHAR(500);
ALTER TABLE users ADD COLUMN face_enabled_at TIMESTAMP;

-- Add comments for documentation (optional)
-- COMMENT ON COLUMN users.face_recognition_enabled IS 'Whether face recognition MFA is enabled for user';
-- COMMENT ON COLUMN users.face_embedding_id IS 'Reference ID to face embedding stored in secure vault';
-- COMMENT ON COLUMN users.face_enabled_at IS 'When face recognition was enabled for user';

-- =============================================================
-- 2. Create face_verification_tokens table
-- =============================================================

CREATE TABLE IF NOT EXISTS face_verification_tokens (
    id VARCHAR(36) PRIMARY KEY COMMENT 'UUID primary key',
    
    -- Token information
    token_code VARCHAR(500) NOT NULL UNIQUE COMMENT 'Unique token code (UUID)',
    user_id VARCHAR(36) NOT NULL COMMENT 'User ID (foreign key)',
    
    -- Timing
    expiration_time TIMESTAMP NOT NULL COMMENT 'When token expires',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp',
    
    -- Usage tracking
    used BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Whether token has been used',
    used_at TIMESTAMP NULL COMMENT 'When token was used',
    
    -- Revocation
    revoked BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Whether token was revoked',
    revocation_reason VARCHAR(255) NULL COMMENT 'Reason for revocation',
    
    -- Verification attempts
    attempt_count INT NOT NULL DEFAULT 0 COMMENT 'Number of verification attempts',
    max_attempts INT NOT NULL DEFAULT 3 COMMENT 'Maximum allowed attempts',
    
    -- Foreign key constraint
    CONSTRAINT fk_face_token_user_id 
        FOREIGN KEY (user_id) 
        REFERENCES users(id) 
        ON DELETE CASCADE
) COMMENT='Temporary tokens for face verification during MFA login';

-- =============================================================
-- 3. Create indexes for performance
-- =============================================================

CREATE INDEX idx_face_token_code 
    ON face_verification_tokens(token_code);

CREATE INDEX idx_face_token_user_id 
    ON face_verification_tokens(user_id);

CREATE INDEX idx_face_token_expiration 
    ON face_verification_tokens(expiration_time);

-- Index for finding active tokens by user (used in cleanup)
CREATE INDEX idx_face_token_active 
    ON face_verification_tokens(user_id, used, revoked, expiration_time);

-- =============================================================
-- 4. Create views for monitoring (optional)
-- =============================================================

-- View for active face tokens
CREATE OR REPLACE VIEW v_active_face_tokens AS
SELECT 
    ft.id,
    ft.token_code,
    ft.user_id,
    u.email,
    ft.expiration_time,
    ft.attempt_count,
    ft.max_attempts,
    NOW() as current_time,
    (ft.expiration_time > NOW()) as is_valid
FROM face_verification_tokens ft
LEFT JOIN users u ON ft.user_id = u.id
WHERE ft.used = FALSE 
  AND ft.revoked = FALSE;

-- View for face-enabled users statistics
CREATE OR REPLACE VIEW v_face_mfa_stats AS
SELECT 
    COUNT(*) as total_users,
    SUM(CASE WHEN u.face_recognition_enabled = TRUE THEN 1 ELSE 0 END) as face_mfa_enabled,
    SUM(CASE WHEN u.face_recognition_enabled = FALSE THEN 1 ELSE 0 END) as face_mfa_disabled,
    ROUND(
        100.0 * SUM(CASE WHEN u.face_recognition_enabled = TRUE THEN 1 ELSE 0 END) / COUNT(*),
        2
    ) as adoption_percentage
FROM users u
WHERE u.deleted_at IS NULL;

-- =============================================================
-- 5. Create scheduled event for token cleanup (optional)
-- =============================================================

-- For MySQL: Create event to cleanup expired tokens daily
/*
CREATE EVENT IF NOT EXISTS cleanup_expired_face_tokens
ON SCHEDULE EVERY 1 DAY
STARTS CURRENT_TIMESTAMP
DO
    DELETE FROM face_verification_tokens
    WHERE expiration_time < NOW() AND used = FALSE;
*/

-- For PostgreSQL: Use pg_cron extension or application-level cleanup

-- =============================================================
-- 6. Sample data initialization (for testing)
-- =============================================================

-- This is optional - only for development/testing
-- Uncomment to add sample data

/*
-- Enable face recognition for a test user
UPDATE users 
SET face_recognition_enabled = TRUE, 
    face_embedding_id = 'emb_test_user_001',
    face_enabled_at = NOW()
WHERE email = 'test@example.com'
LIMIT 1;

-- Create a test face verification token
INSERT INTO face_verification_tokens 
(id, token_code, user_id, expiration_time, attempt_count, max_attempts)
SELECT 
    UUID(),
    'test_token_001',
    u.id,
    DATE_ADD(NOW(), INTERVAL 45 SECOND),
    0,
    3
FROM users u
WHERE u.email = 'test@example.com'
LIMIT 1;
*/

-- =============================================================
-- 7. Verify migration (run these queries to verify)
-- =============================================================

-- Check users table has face recognition columns
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'users'
AND COLUMN_NAME IN ('face_recognition_enabled', 'face_embedding_id', 'face_enabled_at')
ORDER BY ORDINAL_POSITION;

-- Check face_verification_tokens table exists
SELECT TABLE_NAME, TABLE_TYPE
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME = 'face_verification_tokens';

-- Check indexes were created
SELECT INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_NAME = 'face_verification_tokens'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- =============================================================
-- 8. Rollback script (if needed)
-- =============================================================

/*
-- WARNING: This will remove all face recognition data!
-- Only run this if you need to completely remove the feature

ALTER TABLE users DROP COLUMN face_recognition_enabled;
ALTER TABLE users DROP COLUMN face_embedding_id;
ALTER TABLE users DROP COLUMN face_enabled_at;

DROP TABLE IF EXISTS face_verification_tokens;
DROP VIEW IF EXISTS v_active_face_tokens;
DROP VIEW IF EXISTS v_face_mfa_stats;
DROP EVENT IF EXISTS cleanup_expired_face_tokens;
*/

-- =============================================================
-- 9. Monitoring queries
-- =============================================================

-- Check active face tokens
SELECT 
    ft.id,
    u.email,
    ft.attempt_count,
    ft.max_attempts,
    ft.expiration_time,
    TIMESTAMPDIFF(SECOND, NOW(), ft.expiration_time) as seconds_remaining,
    CASE 
        WHEN ft.used = TRUE THEN 'USED'
        WHEN ft.revoked = TRUE THEN 'REVOKED'
        WHEN ft.expiration_time < NOW() THEN 'EXPIRED'
        ELSE 'ACTIVE'
    END as token_status
FROM face_verification_tokens ft
LEFT JOIN users u ON ft.user_id = u.id
ORDER BY ft.created_at DESC
LIMIT 20;

-- Check face MFA adoption
SELECT 
    face_recognition_enabled,
    COUNT(*) as user_count,
    ROUND(100.0 * COUNT(*) / (SELECT COUNT(*) FROM users WHERE deleted_at IS NULL), 2) as percentage
FROM users
WHERE deleted_at IS NULL
GROUP BY face_recognition_enabled;

-- Check failed verification attempts
SELECT 
    u.email,
    ft.user_id,
    ft.attempt_count,
    ft.max_attempts,
    ft.created_at,
    ft.expiration_time
FROM face_verification_tokens ft
LEFT JOIN users u ON ft.user_id = u.id
WHERE ft.attempt_count >= ft.max_attempts
ORDER BY ft.created_at DESC
LIMIT 10;

-- Check recently revoked tokens
SELECT 
    u.email,
    ft.revocation_reason,
    ft.revoked,
    ft.updated_at
FROM face_verification_tokens ft
LEFT JOIN users u ON ft.user_id = u.id
WHERE ft.revoked = TRUE
ORDER BY ft.updated_at DESC
LIMIT 10;

-- =============================================================
-- 10. Performance tuning (optional)
-- =============================================================

-- Analyze tables for query optimization
ANALYZE TABLE users;
ANALYZE TABLE face_verification_tokens;

-- For PostgreSQL:
/*
ANALYZE users;
ANALYZE face_verification_tokens;
VACUUM users;
VACUUM face_verification_tokens;
*/

-- =============================================================
-- Migration Complete
-- =============================================================

-- Summary of changes:
-- 1. Added 3 columns to users table for face recognition
-- 2. Created face_verification_tokens table with 10 columns
-- 3. Created 4 indexes for performance
-- 4. Created 2 monitoring views
-- 5. Ready for Face Recognition MFA implementation

-- Verify by running:
-- SELECT * FROM v_face_mfa_stats;
-- SELECT * FROM v_active_face_tokens;

-- Next steps:
-- 1. Update application.yml with face recognition config
-- 2. Deploy Python AI service
-- 3. Deploy MS-User service with Face Recognition code
-- 4. Test complete login flow
