ALTER TABLE rota_assignment
    ADD COLUMN preferred_team_id BIGINT NULL,
    ADD CONSTRAINT fk_ra_preferred_team
        FOREIGN KEY (preferred_team_id) REFERENCES team(id)
        ON DELETE SET NULL;
