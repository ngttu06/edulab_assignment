ALTER TABLE refresh_sessions ADD COLUMN family_id CHAR(36) NULL;
ALTER TABLE refresh_sessions ADD INDEX idx_refresh_family(family_id);
ALTER TABLE lessons ADD COLUMN prerequisite_id BIGINT NULL;
ALTER TABLE lessons ADD COLUMN required_score DECIMAL(4,2) NOT NULL DEFAULT 0;
ALTER TABLE lessons ADD CONSTRAINT fk_lesson_prerequisite FOREIGN KEY(prerequisite_id) REFERENCES lessons(id);
ALTER TABLE lessons ADD CONSTRAINT chk_lesson_required_score CHECK(required_score BETWEEN 0 AND 10);

CREATE TABLE class_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    classroom_id BIGINT NOT NULL,
    quiz_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    due_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_assignment_class FOREIGN KEY(classroom_id) REFERENCES classrooms(id),
    CONSTRAINT fk_assignment_quiz FOREIGN KEY(quiz_id) REFERENCES quizzes(id),
    CONSTRAINT fk_assignment_teacher FOREIGN KEY(created_by) REFERENCES users(id),
    INDEX idx_assignment_class_due(classroom_id,due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE teacher_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    body VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_feedback_student FOREIGN KEY(student_id) REFERENCES users(id),
    CONSTRAINT fk_feedback_teacher FOREIGN KEY(teacher_id) REFERENCES users(id),
    INDEX idx_feedback_student(student_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE forum_ai_answers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    thread_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    answer TEXT NOT NULL,
    source_label VARCHAR(200) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_forum_ai_thread FOREIGN KEY(thread_id) REFERENCES forum_threads(id),
    CONSTRAINT fk_forum_ai_user FOREIGN KEY(requested_by) REFERENCES users(id),
    INDEX idx_forum_ai_thread(thread_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;