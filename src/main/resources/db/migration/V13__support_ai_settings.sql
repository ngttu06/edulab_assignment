CREATE TABLE ai_settings (
    id INT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    base_url VARCHAR(500) NOT NULL DEFAULT 'https://gatecheap.vn/v1',
    model VARCHAR(120) NOT NULL DEFAULT 'gpt-6.1-sol',
    encrypted_api_key TEXT NULL,
    daily_limit INT NOT NULL DEFAULT 20,
    global_daily_limit INT NOT NULL DEFAULT 200,
    max_output_tokens INT NOT NULL DEFAULT 1000,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_ai_settings_singleton CHECK (id=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO ai_settings(id) VALUES(1);

-- Only operational metadata: no questions, answers or raw IP addresses.
CREATE TABLE support_ai_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_hash CHAR(64) NOT NULL,
    address_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_support_actor_created(actor_hash,created_at),
    INDEX idx_support_address_created(address_hash,created_at),
    INDEX idx_support_created(created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;