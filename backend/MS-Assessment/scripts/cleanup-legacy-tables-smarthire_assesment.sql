-- =============================================================================
-- One-time cleanup: remove OLD Symfony / legacy tables from `smarthire_assesment`
-- (the ones that existed before MS-Assessment Spring was pointed at this DB).
--
-- KEEPS tables used by MS-Assessment (Java):
--   question_category, msa_question, answer_choice,
--   msa_assessment_session, session_answer, user_assessment_assignment
--
-- BACK UP your database before running (Export in phpMyAdmin).
-- Run this in phpMyAdmin → SQL tab, with database `smarthire_assesment` selected.
-- =============================================================================

USE `smarthire_assesment`;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `assessment_history`;
DROP TABLE IF EXISTS `assessment_submission`;
DROP TABLE IF EXISTS `assessment_task`;
DROP TABLE IF EXISTS `generated_question`;
DROP TABLE IF EXISTS `question_answer`;
DROP TABLE IF EXISTS `user_answer`;
DROP TABLE IF EXISTS `skill_profile_snapshot`;
DROP TABLE IF EXISTS `skill_profile`;
-- Legacy names (not the Spring tables msa_assessment_session / msa_question):
DROP TABLE IF EXISTS `assessment_session`;
DROP TABLE IF EXISTS `question`;

SET FOREIGN_KEY_CHECKS = 1;
