-- Patch: ensure project_suggestion.created_at exists for roadmap challenge history queries.
-- Safe to run multiple times on MySQL/MariaDB variants that support IF NOT EXISTS.

ALTER TABLE project_suggestion
ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;

UPDATE project_suggestion
SET created_at = NOW()
WHERE created_at IS NULL;
