ALTER TABLE team
    ADD COLUMN members_count INT NOT NULL DEFAULT 0 AFTER description;