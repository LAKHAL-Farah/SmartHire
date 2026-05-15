-- Job applications schema

CREATE TABLE IF NOT EXISTS job_applications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    resume_url VARCHAR(1024) NOT NULL,
    status VARCHAR(50) NOT NULL,
    applied_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_job_applications_job
        FOREIGN KEY (job_id) REFERENCES jobs(id)
        ON DELETE CASCADE,
    CONSTRAINT uq_job_applications_job_user
        UNIQUE (job_id, user_id)
);

CREATE INDEX idx_job_applications_job_id ON job_applications(job_id);
CREATE INDEX idx_job_applications_user_id ON job_applications(user_id);

