CREATE TABLE forum_categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    classroom_id BIGINT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_forum_category_class FOREIGN KEY (classroom_id) REFERENCES classrooms(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE forum_threads (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    title VARCHAR(240) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_thread_category FOREIGN KEY (category_id) REFERENCES forum_categories(id),
    CONSTRAINT fk_thread_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT chk_thread_status CHECK (status IN ('PENDING', 'PUBLISHED', 'HIDDEN', 'LOCKED', 'REMOVED')),
    INDEX idx_threads_category_created (category_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE forum_posts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    thread_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_post_thread FOREIGN KEY (thread_id) REFERENCES forum_threads(id),
    CONSTRAINT fk_post_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT chk_post_status CHECK (status IN ('PUBLISHED', 'HIDDEN', 'REMOVED')),
    INDEX idx_posts_thread_created (thread_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE stored_files (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    storage_key VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    scan_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_SCAN',
    uploaded_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_stored_file_key UNIQUE (storage_key),
    CONSTRAINT fk_file_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id),
    CONSTRAINT chk_file_scan_status CHECK (scan_status IN ('PENDING_SCAN', 'CLEAN', 'REJECTED', 'INFECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE forum_post_attachments (
    forum_post_id BIGINT NOT NULL,
    stored_file_id BIGINT NOT NULL,
    PRIMARY KEY (forum_post_id, stored_file_id),
    CONSTRAINT fk_attachment_post FOREIGN KEY (forum_post_id) REFERENCES forum_posts(id),
    CONSTRAINT fk_attachment_file FOREIGN KEY (stored_file_id) REFERENCES stored_files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE content_reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reporter_id BIGINT NOT NULL,
    thread_id BIGINT NULL,
    post_id BIGINT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    handled_by BIGINT NULL,
    handled_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
    CONSTRAINT fk_report_thread FOREIGN KEY (thread_id) REFERENCES forum_threads(id),
    CONSTRAINT fk_report_post FOREIGN KEY (post_id) REFERENCES forum_posts(id),
    CONSTRAINT fk_report_handler FOREIGN KEY (handled_by) REFERENCES users(id),
    CONSTRAINT chk_report_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO forum_categories (name, description) VALUES
('Hỏi bài KHTN 8', 'Nơi trao đổi các câu hỏi về bài học và bài tập KHTN lớp 8.'),
('Góc thí nghiệm an toàn', 'Chia sẻ quan sát và thảo luận thí nghiệm an toàn tại nhà/lớp học.');
