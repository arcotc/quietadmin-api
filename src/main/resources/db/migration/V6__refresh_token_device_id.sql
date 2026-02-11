ALTER TABLE refresh_token
    ADD COLUMN device_id VARCHAR(64) NULL AFTER user_id;

CREATE INDEX idx_refresh_token_user_device
    ON refresh_token (user_id, device_id);