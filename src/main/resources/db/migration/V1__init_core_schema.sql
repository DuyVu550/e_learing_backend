CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(500) NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE courses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slug VARCHAR(180) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    thumbnail_url VARCHAR(500) NULL,
    level VARCHAR(30) NULL,
    status VARCHAR(30) NOT NULL,
    instructor_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_courses_slug UNIQUE (slug),
    CONSTRAINT fk_courses_instructor FOREIGN KEY (instructor_id) REFERENCES users(id),
    INDEX idx_courses_instructor_id (instructor_id),
    INDEX idx_courses_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_sections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    position INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_course_sections_course FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT uk_course_sections_course_position UNIQUE (course_id, position),
    INDEX idx_course_sections_course_id (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lessons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    section_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content LONGTEXT NULL,
    video_url VARCHAR(500) NULL,
    duration_seconds INT NULL,
    position INT NOT NULL,
    is_preview BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lessons_section FOREIGN KEY (section_id) REFERENCES course_sections(id),
    CONSTRAINT uk_lessons_section_position UNIQUE (section_id, position),
    INDEX idx_lessons_section_id (section_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE enrollments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    enrolled_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_enrollments_user_course UNIQUE (user_id, course_id),
    CONSTRAINT fk_enrollments_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_enrollments_course FOREIGN KEY (course_id) REFERENCES courses(id),
    INDEX idx_enrollments_user_id (user_id),
    INDEX idx_enrollments_course_id (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lesson_progress (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    last_position_seconds INT NULL,
    completed_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lesson_progress_user_lesson UNIQUE (user_id, lesson_id),
    CONSTRAINT fk_lesson_progress_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_lesson_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id),
    INDEX idx_lesson_progress_user_id (user_id),
    INDEX idx_lesson_progress_lesson_id (lesson_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assessments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    lesson_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    time_limit_minutes INT NULL,
    max_attempts INT NULL,
    passing_score DECIMAL(5,2) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_assessments_course FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_assessments_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id),
    INDEX idx_assessments_course_id (course_id),
    INDEX idx_assessments_lesson_id (lesson_id),
    INDEX idx_assessments_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assessment_id BIGINT NOT NULL,
    question_text TEXT NOT NULL,
    type VARCHAR(30) NOT NULL,
    points DECIMAL(8,2) NOT NULL,
    position INT NOT NULL,
    explanation TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_questions_assessment FOREIGN KEY (assessment_id) REFERENCES assessments(id),
    CONSTRAINT uk_questions_assessment_position UNIQUE (assessment_id, position),
    INDEX idx_questions_assessment_id (assessment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE question_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    option_text TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    position INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_question_options_question FOREIGN KEY (question_id) REFERENCES questions(id),
    CONSTRAINT uk_question_options_question_position UNIQUE (question_id, position),
    INDEX idx_question_options_question_id (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assessment_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assessment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    submitted_at DATETIME(6) NULL,
    score DECIMAL(8,2) NULL,
    max_score DECIMAL(8,2) NULL,
    passed BOOLEAN NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_attempts_assessment_user_no UNIQUE (assessment_id, user_id, attempt_no),
    CONSTRAINT fk_attempts_assessment FOREIGN KEY (assessment_id) REFERENCES assessments(id),
    CONSTRAINT fk_attempts_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_attempts_user_id (user_id),
    INDEX idx_attempts_assessment_id (assessment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer_text TEXT NULL,
    is_correct BOOLEAN NULL,
    score DECIMAL(8,2) NULL,
    graded_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_answers_attempt_question UNIQUE (attempt_id, question_id),
    CONSTRAINT fk_answers_attempt FOREIGN KEY (attempt_id) REFERENCES assessment_attempts(id),
    CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES questions(id),
    INDEX idx_answers_attempt_id (attempt_id),
    INDEX idx_answers_question_id (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE answer_options (
    answer_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    PRIMARY KEY (answer_id, option_id),
    CONSTRAINT fk_answer_options_answer FOREIGN KEY (answer_id) REFERENCES answers(id),
    CONSTRAINT fk_answer_options_option FOREIGN KEY (option_id) REFERENCES question_options(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO roles (code, name) VALUES
('ADMIN', 'Administrator'),
('INSTRUCTOR', 'Instructor'),
('STUDENT', 'Student');
