-- Additive only: preserve provider, encrypted key, quotas and all existing data.
-- NULL uses the application's default prompt; admins can reset by submitting blank.
ALTER TABLE ai_settings
    ADD COLUMN system_prompt TEXT NULL,
    ADD COLUMN support_system_prompt TEXT NULL;