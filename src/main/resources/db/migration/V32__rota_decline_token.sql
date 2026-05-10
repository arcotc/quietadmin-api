CREATE TABLE rota_decline_token (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rdt_assignment
        FOREIGN KEY (assignment_id) REFERENCES rota_assignment(id)
        ON DELETE CASCADE,

    UNIQUE KEY uq_rdt_token_hash (token_hash),
    INDEX idx_rdt_assignment (assignment_id)
);
