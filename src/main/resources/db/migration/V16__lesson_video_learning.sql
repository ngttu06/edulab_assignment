ALTER TABLE lessons ADD COLUMN video_teacher_id BIGINT NULL;
ALTER TABLE lessons ADD CONSTRAINT fk_lesson_video_teacher FOREIGN KEY (video_teacher_id) REFERENCES users(id);

CREATE TABLE lesson_videos (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lesson_id BIGINT NOT NULL,
    revision INT NOT NULL,
    provider VARCHAR(16) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    youtube_id VARCHAR(11) NULL,
    duration_seconds INT NOT NULL,
    completion_threshold TINYINT NOT NULL DEFAULT 95,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_lesson_video_revision UNIQUE (lesson_id, revision),
    CONSTRAINT fk_video_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id),
    CONSTRAINT fk_video_author FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT chk_video_provider CHECK (provider IN ('YOUTUBE', 'PLAYERJS')),
    CONSTRAINT chk_video_duration CHECK (duration_seconds BETWEEN 1 AND 21600),
    CONSTRAINT chk_video_threshold CHECK (completion_threshold BETWEEN 80 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE lessons ADD COLUMN video_id BIGINT NULL;
ALTER TABLE lessons ADD CONSTRAINT fk_lesson_current_video FOREIGN KEY (video_id) REFERENCES lesson_videos(id);

CREATE TABLE video_progress (
    student_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    position_ms BIGINT NOT NULL DEFAULT 0,
    ranges_json JSON NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (student_id, video_id),
    CONSTRAINT fk_video_progress_student FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT fk_video_progress_video FOREIGN KEY (video_id) REFERENCES lesson_videos(id),
    CONSTRAINT chk_video_position CHECK (position_ms >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE video_watch_sessions (
    id CHAR(36) PRIMARY KEY,
    student_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    sequence_number INT NOT NULL DEFAULT 0,
    credit_ms BIGINT NOT NULL DEFAULT 2000,
    last_report_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_watch_student FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT fk_watch_video FOREIGN KEY (video_id) REFERENCES lesson_videos(id),
    INDEX idx_watch_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;