ALTER TABLE courses
    ADD COLUMN price DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER thumbnail_url;

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_code BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    description VARCHAR(255) NULL,
    checkout_url VARCHAR(500) NULL,
    payment_link_id VARCHAR(100) NULL,
    transaction_reference VARCHAR(100) NULL,
    paid_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    failure_reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_order_code UNIQUE (order_code),
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_payments_course FOREIGN KEY (course_id) REFERENCES courses(id),
    INDEX idx_payments_user_id (user_id),
    INDEX idx_payments_course_id (course_id),
    INDEX idx_payments_user_course_status (user_id, course_id, status),
    INDEX idx_payments_status_paid_at (status, paid_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
