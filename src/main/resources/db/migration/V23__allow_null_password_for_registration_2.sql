ALTER TABLE pending_signup
    MODIFY COLUMN password_hash VARCHAR(255) NULL;