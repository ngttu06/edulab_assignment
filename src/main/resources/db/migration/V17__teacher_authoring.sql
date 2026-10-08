ALTER TABLE lessons ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN class_only BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT fk_lesson_creator FOREIGN KEY(created_by) REFERENCES users(id);
ALTER TABLE quizzes ADD COLUMN class_only BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE class_lessons (
    classroom_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    PRIMARY KEY(classroom_id,lesson_id),
    CONSTRAINT fk_class_lesson_class FOREIGN KEY(classroom_id) REFERENCES classrooms(id),
    CONSTRAINT fk_class_lesson_lesson FOREIGN KEY(lesson_id) REFERENCES lessons(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_classes (
    classroom_id BIGINT NOT NULL,
    quiz_id BIGINT NOT NULL,
    PRIMARY KEY(classroom_id,quiz_id),
    CONSTRAINT fk_quiz_class_class FOREIGN KEY(classroom_id) REFERENCES classrooms(id),
    CONSTRAINT fk_quiz_class_quiz FOREIGN KEY(quiz_id) REFERENCES quizzes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;