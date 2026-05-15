-- Add forfeit column to msa_assessment_session table
-- This column tracks when a candidate clicks "Back" without submitting

ALTER TABLE msa_assessment_session 
ADD COLUMN forfeit BOOLEAN NOT NULL DEFAULT FALSE;

-- Verify the column was added
DESCRIBE msa_assessment_session;
