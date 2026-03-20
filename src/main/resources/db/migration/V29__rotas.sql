DROP TABLE IF EXISTS rota_entry;
DROP TABLE IF EXISTS rota;

CREATE TABLE rota (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    rota_date DATE NOT NULL,
    group_id BIGINT NOT NULL,
    team_id BIGINT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rota_group_date (group_id, rota_date)
);

CREATE TABLE rota_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rota_id BIGINT NOT NULL,
    role_name VARCHAR(120) NOT NULL,
    user_id BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rota_assignment_rota
        FOREIGN KEY (rota_id) REFERENCES rota(id)
        ON DELETE CASCADE,
    INDEX idx_rota_assignment_rota (rota_id),
INDEX idx_rota_assignment_user (user_id)
);