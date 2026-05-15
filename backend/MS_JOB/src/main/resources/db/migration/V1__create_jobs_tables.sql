-- Initial schema for MS_JOB
-- Matches tn.esprit.msjob.entity.Job

CREATE TABLE IF NOT EXISTS jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    company VARCHAR(255) NOT NULL,
    company_initials VARCHAR(10) NULL,
    company_color VARCHAR(7) NULL,
    verified BIT(1) NULL,
    location_type VARCHAR(255) NULL,
    contract_type VARCHAR(255) NULL,
    salary_range VARCHAR(50) NULL,
    experience_level VARCHAR(255) NULL,
    description TEXT NULL,
    posted_date DATETIME(6) NULL,
    user_id BIGINT NULL,
    PRIMARY KEY (id)
);

-- ElementCollection for Job.skills
CREATE TABLE IF NOT EXISTS job_skills (
    job_id BIGINT NOT NULL,
    skill VARCHAR(255) NULL,
    CONSTRAINT fk_job_skills_job
        FOREIGN KEY (job_id) REFERENCES jobs(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_job_skills_job_id ON job_skills(job_id);

