CREATE TABLE question_topics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_question_topics_course FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT uk_question_topics_course_name UNIQUE (course_id, name),
    INDEX idx_question_topics_course_id (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE assessments
    ADD COLUMN composition_mode VARCHAR(30) NOT NULL DEFAULT 'FIXED' AFTER status,
    ADD COLUMN available_from DATETIME(6) NULL AFTER composition_mode,
    ADD COLUMN available_until DATETIME(6) NULL AFTER available_from,
    ADD COLUMN shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE AFTER passing_score,
    ADD COLUMN shuffle_options BOOLEAN NOT NULL DEFAULT FALSE AFTER shuffle_questions;

ALTER TABLE questions
    ADD COLUMN course_id BIGINT NULL AFTER id,
    ADD COLUMN topic_id BIGINT NULL AFTER course_id,
    ADD COLUMN difficulty VARCHAR(30) NOT NULL DEFAULT 'EASY' AFTER type,
    ADD COLUMN expected_answer TEXT NULL AFTER points;

UPDATE questions q
JOIN assessments a ON q.assessment_id = a.id
SET q.course_id = a.course_id;

CREATE TABLE assessment_question_selections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assessment_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    position INT NOT NULL,
    points DECIMAL(8,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_assessment_question_selections_assessment FOREIGN KEY (assessment_id) REFERENCES assessments(id),
    CONSTRAINT fk_assessment_question_selections_question FOREIGN KEY (question_id) REFERENCES questions(id),
    CONSTRAINT uk_assessment_question_selections_question UNIQUE (assessment_id, question_id),
    CONSTRAINT uk_assessment_question_selections_position UNIQUE (assessment_id, position),
    INDEX idx_assessment_question_selections_assessment_id (assessment_id),
    INDEX idx_assessment_question_selections_question_id (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO assessment_question_selections (assessment_id, question_id, position, points, created_at, updated_at)
SELECT assessment_id, id, position, points, created_at, updated_at
FROM questions;

ALTER TABLE questions
    MODIFY course_id BIGINT NOT NULL;

ALTER TABLE questions
    ADD CONSTRAINT fk_questions_course FOREIGN KEY (course_id) REFERENCES courses(id),
    ADD CONSTRAINT fk_questions_topic FOREIGN KEY (topic_id) REFERENCES question_topics(id),
    ADD INDEX idx_questions_course_id (course_id),
    ADD INDEX idx_questions_topic_id (topic_id),
    ADD INDEX idx_questions_difficulty (difficulty),
    ADD INDEX idx_questions_type (type);

ALTER TABLE questions
    DROP FOREIGN KEY fk_questions_assessment;

ALTER TABLE questions
    DROP INDEX uk_questions_assessment_position,
    DROP INDEX idx_questions_assessment_id,
    DROP COLUMN assessment_id,
    DROP COLUMN position;

CREATE TABLE assessment_question_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assessment_id BIGINT NOT NULL,
    topic_id BIGINT NULL,
    difficulty VARCHAR(30) NULL,
    question_type VARCHAR(30) NULL,
    question_count INT NOT NULL,
    points DECIMAL(8,2) NOT NULL,
    position INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_assessment_question_rules_assessment FOREIGN KEY (assessment_id) REFERENCES assessments(id),
    CONSTRAINT fk_assessment_question_rules_topic FOREIGN KEY (topic_id) REFERENCES question_topics(id),
    CONSTRAINT uk_assessment_question_rules_position UNIQUE (assessment_id, position),
    INDEX idx_assessment_question_rules_assessment_id (assessment_id),
    INDEX idx_assessment_question_rules_topic_id (topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
