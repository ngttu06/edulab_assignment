ALTER TABLE quiz_attempts
    ADD COLUMN title_snapshot VARCHAR(200) NULL,
    ADD COLUMN questions_snapshot JSON NULL,
    ADD COLUMN answers_json JSON NULL,
    ADD COLUMN deadline_at TIMESTAMP(6) NULL,
    ADD COLUMN max_score DECIMAL(10,2) NULL,
    ADD COLUMN expired BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_attempt_active ON quiz_attempts (student_id, quiz_id, status);
