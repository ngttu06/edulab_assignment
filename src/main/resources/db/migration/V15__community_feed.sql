-- Additive only; preserves existing discussions, attachments and AI answers.
CREATE TABLE forum_thread_likes (
    thread_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (thread_id, user_id),
    CONSTRAINT fk_thread_like_thread FOREIGN KEY (thread_id) REFERENCES forum_threads(id),
    CONSTRAINT fk_thread_like_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_thread_likes_user (user_id, thread_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_threads_feed ON forum_threads(status, id);
CREATE INDEX idx_threads_category_feed ON forum_threads(category_id, status, id);
CREATE INDEX idx_posts_thread_cursor ON forum_posts(thread_id, status, id);