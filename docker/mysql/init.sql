-- Initialize required databases and create application user
CREATE DATABASE IF NOT EXISTS msroadmap CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS user_service1 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS profile_ms_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smarthire_assesment CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS job_service CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS interview_ms_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create application user and grant privileges
CREATE USER IF NOT EXISTS 'smarthire_app'@'%' IDENTIFIED BY 'change_me_app_password';
GRANT ALL PRIVILEGES ON msroadmap.* TO 'smarthire_app'@'%';
GRANT ALL PRIVILEGES ON user_service1.* TO 'smarthire_app'@'%';
GRANT ALL PRIVILEGES ON profile_ms_db.* TO 'smarthire_app'@'%';
GRANT ALL PRIVILEGES ON smarthire_assesment.* TO 'smarthire_app'@'%';
GRANT ALL PRIVILEGES ON job_service.* TO 'smarthire_app'@'%';
GRANT ALL PRIVILEGES ON interview_ms_db.* TO 'smarthire_app'@'%';
FLUSH PRIVILEGES;
