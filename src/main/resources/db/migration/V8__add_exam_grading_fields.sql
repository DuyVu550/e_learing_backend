ALTER TABLE answers
    ADD COLUMN flagged BOOLEAN NOT NULL DEFAULT FALSE AFTER answer_text,
    ADD COLUMN feedback TEXT NULL AFTER graded_at,
    ADD COLUMN graded_by BIGINT NULL AFTER feedback,
    ADD CONSTRAINT fk_answers_graded_by FOREIGN KEY (graded_by) REFERENCES users(id),
    ADD INDEX idx_answers_graded_by (graded_by);

ALTER TABLE assessments
    ADD COLUMN show_answers_after_submit BOOLEAN NOT NULL DEFAULT FALSE AFTER shuffle_options;
