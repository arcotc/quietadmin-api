CREATE TABLE rota_user_decline_token (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rota_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP   NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rudct_rota
        FOREIGN KEY (rota_id) REFERENCES rota(id)
        ON DELETE CASCADE,

    UNIQUE KEY uq_rudct_token_hash (token_hash),
    INDEX idx_rudct_rota_user (rota_id, user_id)
);
