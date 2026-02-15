CREATE TABLE pending_signup
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,

    token         VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    first_name    VARCHAR(255) NOT NULL,
    group_name    VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at    TIMESTAMP    NOT NULL,

    CONSTRAINT uk_pending_signup_token UNIQUE (token)
);

CREATE INDEX ix_pending_signup_expires
    ON pending_signup (expires_at);