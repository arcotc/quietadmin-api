-- ======================================================
-- V4: User Status + Subscription Lifecycle
-- ======================================================

-- ===============================
-- 1️⃣ Add status to user_account
-- ===============================

ALTER TABLE user_account
    ADD COLUMN status VARCHAR(20)
        NOT NULL
        DEFAULT 'INVITED';

-- Constrain allowed values (MySQL CHECK works in 8+)
ALTER TABLE user_account
    ADD CONSTRAINT chk_user_status
        CHECK (status IN ('ACTIVE', 'INVITED', 'SUSPENDED'));


-- ===============================
-- 2️⃣ Add trial_ends_at to qa_group
-- ===============================

ALTER TABLE qa_group
    ADD COLUMN trial_ends_at TIMESTAMP NULL;


-- ===============================
-- 3️⃣ Add Stripe customer reference
-- ===============================

ALTER TABLE qa_group
    ADD COLUMN stripe_customer_id VARCHAR(255) NULL;

-- Stripe IDs are globally unique — safe to index
CREATE UNIQUE INDEX uk_qa_group_stripe_customer
    ON qa_group (stripe_customer_id);


-- ===============================
-- 4️⃣ Helpful indexes
-- ===============================

CREATE INDEX idx_user_status
    ON user_account (status);

CREATE INDEX idx_group_trial_ends
    ON qa_group (trial_ends_at);