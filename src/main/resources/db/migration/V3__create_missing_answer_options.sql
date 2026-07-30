CREATE TABLE IF NOT EXISTS answer_options (
    answer_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    PRIMARY KEY (answer_id, option_id),
    CONSTRAINT fk_answer_options_answer FOREIGN KEY (answer_id) REFERENCES answers(id),
    CONSTRAINT fk_answer_options_option FOREIGN KEY (option_id) REFERENCES question_options(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
