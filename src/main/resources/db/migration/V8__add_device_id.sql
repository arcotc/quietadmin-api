-- Vx__add_device_id.sql

CREATE INDEX idx_refresh_token_device_id
    ON refresh_token (device_id);

ALTER TABLE refresh_token
    ADD COLUMN fingerprint_hash VARCHAR(128);

ALTER TABLE user_account
    ADD COLUMN email_verified BOOLEAN DEFAULT FALSE,
ADD COLUMN email_verification_token VARCHAR(255),
ADD COLUMN email_verification_expires_at TIMESTAMP NULL;

CREATE TABLE invitation
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    email       VARCHAR(255)        NOT NULL,
    group_id    BIGINT              NOT NULL,
    role        VARCHAR(50)         NOT NULL,
    token_hash  VARCHAR(255) UNIQUE NOT NULL,
    expires_at  TIMESTAMP           NOT NULL,
    accepted_at TIMESTAMP NULL
);

ALTER TABLE invitation
    ADD CONSTRAINT fk_invitation_group
        FOREIGN KEY (group_id)
            REFERENCES qa_group(id);

CREATE INDEX idx_user_email_verification_token
    ON user_account(email_verification_token);