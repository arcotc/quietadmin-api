CREATE TABLE refresh_token
(
    id                     BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id                BIGINT       NOT NULL,

    -- store hash only (never store raw token)
    token_hash             VARCHAR(255) NOT NULL UNIQUE,

    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at             TIMESTAMP    NOT NULL,
    revoked_at             TIMESTAMP NULL,

    -- optional hardening fields
    replaced_by_token_hash VARCHAR(255) NULL,
    last_used_at           TIMESTAMP NULL,
    user_agent             VARCHAR(255) NULL,
    ip_address             VARCHAR(64) NULL,

    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES user_account (id),

    INDEX                  idx_refresh_user (user_id),
    INDEX                  idx_refresh_expires (expires_at)
);