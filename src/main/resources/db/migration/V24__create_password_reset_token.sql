CREATE TABLE password_reset_token
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    INDEX      idx_prt_user (user_id),
    INDEX      idx_prt_token_hash (token_hash),
    INDEX      idx_prt_expires (expires_at)
);