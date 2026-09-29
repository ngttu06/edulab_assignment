CREATE TABLE subjects (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(160) NOT NULL,
    grade_level TINYINT NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE topics (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    subject_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_topic_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    INDEX idx_topics_subject_order (subject_id, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lessons (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    summary VARCHAR(500) NULL,
    duration_minutes INT NOT NULL DEFAULT 30,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_lesson_slug UNIQUE (slug),
    CONSTRAINT fk_lesson_topic FOREIGN KEY (topic_id) REFERENCES topics(id),
    CONSTRAINT chk_lesson_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    INDEX idx_lessons_topic_status (topic_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lesson_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lesson_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    objectives TEXT NULL,
    content_html MEDIUMTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_lesson_version UNIQUE (lesson_id, version_number),
    CONSTRAINT fk_version_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id),
    CONSTRAINT fk_version_author FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT chk_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lesson_progress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    completion_percent TINYINT NOT NULL DEFAULT 0,
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_lesson_progress UNIQUE (student_id, lesson_id),
    CONSTRAINT fk_progress_student FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT fk_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id),
    CONSTRAINT chk_progress_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_progress_percent CHECK (completion_percent BETWEEN 0 AND 100),
    INDEX idx_progress_student (student_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
