CREATE TABLE question_banks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    subject_id BIGINT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_bank_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_bank_author FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE questions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bank_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    explanation TEXT NULL,
    difficulty VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_question_bank FOREIGN KEY (bank_id) REFERENCES question_banks(id),
    CONSTRAINT chk_question_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE question_options (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    option_text VARCHAR(1000) NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_option_question FOREIGN KEY (question_id) REFERENCES questions(id),
    INDEX idx_options_question (question_id, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quizzes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    lesson_id BIGINT NULL,
    duration_minutes INT NOT NULL DEFAULT 15,
    max_attempts INT NOT NULL DEFAULT 1,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NULL,
    CONSTRAINT fk_quiz_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id),
    CONSTRAINT fk_quiz_author FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_items (
    quiz_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    display_order INT NOT NULL,
    points DECIMAL(6,2) NOT NULL DEFAULT 1,
    PRIMARY KEY (quiz_id, question_id),
    CONSTRAINT fk_item_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes(id),
    CONSTRAINT fk_item_question FOREIGN KEY (question_id) REFERENCES questions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_attempts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quiz_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    score DECIMAL(6,2) NULL,
    started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    submitted_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_attempt_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes(id),
    CONSTRAINT fk_attempt_student FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT uq_attempt_number UNIQUE (quiz_id, student_id, attempt_number),
    CONSTRAINT chk_attempt_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'GRADED')),
    INDEX idx_attempt_student (student_id, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
