CREATE TABLE quiz_tab_events (
    attempt_id BIGINT NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (attempt_id,event_id),
    CONSTRAINT fk_tab_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_ai_reviews (
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL DEFAULT 0,
    review_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    content TEXT NULL,
    provider VARCHAR(80) NULL,
    sample BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (attempt_id,question_id,review_type),
    CONSTRAINT fk_review_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempts(id),
    CONSTRAINT chk_review_status CHECK (status IN ('PENDING','SUCCEEDED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_import_sessions (
    id VARCHAR(36) PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    filename VARCHAR(255) NOT NULL,
    questions_json JSON NOT NULL,
    warnings_json JSON NOT NULL,
    generation_status VARCHAR(20) NOT NULL DEFAULT 'READY',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_import_teacher FOREIGN KEY (teacher_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;