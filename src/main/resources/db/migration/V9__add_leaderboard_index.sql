ALTER TABLE assessment_attempts
    ADD INDEX idx_attempts_assessment_status_score (assessment_id, status, score);
