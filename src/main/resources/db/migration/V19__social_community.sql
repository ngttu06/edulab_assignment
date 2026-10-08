-- Independent social feed. Historical forum questions and quiz data remain untouched.
CREATE TABLE community_posts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    author_id BIGINT NOT NULL,
    body VARCHAR(3000) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_community_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT chk_community_post_status CHECK (status IN ('PUBLISHED','HIDDEN','REMOVED')),
    INDEX idx_community_feed (status,id),
    INDEX idx_community_author (author_id,status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE community_images (
    post_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    position SMALLINT NOT NULL,
    PRIMARY KEY (post_id,file_id),
    UNIQUE KEY uq_community_image_position (post_id,position),
    UNIQUE KEY uq_community_image_file (file_id),
    CONSTRAINT fk_community_image_post FOREIGN KEY (post_id) REFERENCES community_posts(id),
    CONSTRAINT fk_community_image_file FOREIGN KEY (file_id) REFERENCES stored_files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE community_comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    body VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_community_comment_post FOREIGN KEY (post_id) REFERENCES community_posts(id),
    CONSTRAINT fk_community_comment_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT chk_community_comment_status CHECK (status IN ('PUBLISHED','REMOVED')),
    INDEX idx_community_comment_cursor (post_id,status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE community_likes (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (post_id,user_id),
    CONSTRAINT fk_community_like_post FOREIGN KEY (post_id) REFERENCES community_posts(id),
    CONSTRAINT fk_community_like_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE community_reports (
    post_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (post_id,reporter_id),
    CONSTRAINT fk_community_report_post FOREIGN KEY (post_id) REFERENCES community_posts(id),
    CONSTRAINT fk_community_report_user FOREIGN KEY (reporter_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;