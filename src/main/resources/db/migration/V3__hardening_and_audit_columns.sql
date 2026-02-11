-- ======================================================
-- V2: Hardening & Audit Columns
-- ======================================================

-- ===============================
-- 1️⃣ Enforce case-sensitive email
-- ===============================

ALTER TABLE user_account
    MODIFY email VARCHAR(255)
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_bin
    NOT NULL;

-- NOTE:
-- You MUST enforce lowercase in application layer
-- e.g. email.toLowerCase() before save


-- ===============================
-- 2️⃣ Add deleted_at (soft delete)
-- ===============================

ALTER TABLE user_account
    ADD COLUMN deleted_at TIMESTAMP NULL;

ALTER TABLE qa_group
    ADD COLUMN deleted_at TIMESTAMP NULL;


-- ===============================
-- 3️⃣ Add updated_at (audit consistency)
-- ===============================

-- user_account
ALTER TABLE user_account
    ADD COLUMN updated_at TIMESTAMP
        DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP;

-- qa_group
ALTER TABLE qa_group
    ADD COLUMN updated_at TIMESTAMP
        DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP;

-- rota
ALTER TABLE rota
    ADD COLUMN updated_at TIMESTAMP
        DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP;

-- notice
ALTER TABLE notice
    ADD COLUMN updated_at TIMESTAMP
        DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP;

-- resource
ALTER TABLE resource
    ADD COLUMN updated_at TIMESTAMP
        DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP;

