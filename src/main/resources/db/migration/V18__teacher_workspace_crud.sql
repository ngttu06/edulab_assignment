-- Tombstones retain foreign keys, learning progress and attempt snapshots.
ALTER TABLE lessons ADD COLUMN deleted_at TIMESTAMP(6) NULL;
ALTER TABLE quizzes ADD COLUMN deleted_at TIMESTAMP(6) NULL;
ALTER TABLE classrooms ADD COLUMN archived_at TIMESTAMP(6) NULL;
ALTER TABLE class_assignments ADD COLUMN cancelled_at TIMESTAMP(6) NULL;
ALTER TABLE teacher_feedback ADD COLUMN deleted_at TIMESTAMP(6) NULL;
ALTER TABLE teacher_feedback ADD COLUMN updated_at TIMESTAMP(6) NULL;

CREATE TABLE teacher_feedback_revisions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    feedback_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    body VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_feedback_revision_feedback FOREIGN KEY(feedback_id) REFERENCES teacher_feedback(id),
    CONSTRAINT fk_feedback_revision_actor FOREIGN KEY(actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;