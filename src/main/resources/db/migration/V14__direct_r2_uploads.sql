-- Profiles remain addressable by older files when the active bucket changes.
CREATE TABLE storage_profiles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    endpoint VARCHAR(500) NOT NULL,
    bucket VARCHAR(63) NOT NULL,
    region VARCHAR(63) NOT NULL,
    object_prefix VARCHAR(120) NOT NULL,
    encrypted_access_key TEXT NOT NULL,
    encrypted_secret_key TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE storage_settings (
    id INT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    active_profile_id BIGINT NULL,
    max_image_bytes BIGINT NOT NULL DEFAULT 5242880,
    max_pdf_bytes BIGINT NOT NULL DEFAULT 10485760,
    user_quota_bytes BIGINT NOT NULL DEFAULT 104857600,
    ai_images_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ai_pdf_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_storage_singleton CHECK (id=1),
    CONSTRAINT fk_storage_active FOREIGN KEY (active_profile_id) REFERENCES storage_profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO storage_settings(id) VALUES(1);

ALTER TABLE stored_files
    ADD COLUMN storage_profile_id BIGINT NULL,
    ADD CONSTRAINT fk_file_storage_profile FOREIGN KEY (storage_profile_id) REFERENCES storage_profiles(id);

CREATE TABLE file_uploads (
    id CHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_id BIGINT NOT NULL,
    staging_key VARCHAR(255) NOT NULL,
    final_key VARCHAR(255) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    expected_bytes BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    stored_file_id BIGINT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_upload_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_upload_profile FOREIGN KEY (profile_id) REFERENCES storage_profiles(id),
    CONSTRAINT fk_upload_file FOREIGN KEY (stored_file_id) REFERENCES stored_files(id),
    CONSTRAINT chk_upload_status CHECK (status IN ('PENDING','COMPLETED','REJECTED','CANCELLED','EXPIRED')),
    INDEX idx_upload_user_status(user_id,status,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE forum_thread_attachments (
    thread_id BIGINT NOT NULL,
    stored_file_id BIGINT NOT NULL,
    PRIMARY KEY (thread_id,stored_file_id),
    CONSTRAINT fk_thread_attachment_thread FOREIGN KEY (thread_id) REFERENCES forum_threads(id),
    CONSTRAINT fk_thread_attachment_file FOREIGN KEY (stored_file_id) REFERENCES stored_files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE forum_threads ADD COLUMN ai_lesson_id BIGINT NULL,
    ADD CONSTRAINT fk_thread_ai_lesson FOREIGN KEY (ai_lesson_id) REFERENCES lessons(id);