ALTER TABLE user_account
    ADD COLUMN first_name VARCHAR(100) NULL AFTER email,
    ADD COLUMN last_name VARCHAR(100) NULL AFTER first_name;
