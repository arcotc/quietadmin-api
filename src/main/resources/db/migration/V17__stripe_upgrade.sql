ALTER TABLE qa_group
  DROP COLUMN subscription_status,
  DROP COLUMN trial_ends_at;

ALTER TABLE qa_group
  ADD COLUMN stripe_subscription_id    VARCHAR(64) NULL,
  ADD COLUMN stripe_subscription_status VARCHAR(32) NULL,
  ADD COLUMN stripe_trial_end          TIMESTAMP NULL,
  ADD COLUMN stripe_current_period_end TIMESTAMP NULL,
  ADD COLUMN stripe_cancel_at          TIMESTAMP NULL,
  ADD COLUMN stripe_canceled_at        TIMESTAMP NULL,
  ADD COLUMN stripe_last_event_at      TIMESTAMP NULL,
  ADD COLUMN stripe_last_sync_at       TIMESTAMP NULL;

-- Helpful indexes
CREATE INDEX idx_qagroup_stripe_subscription_id ON qa_group (stripe_subscription_id);
CREATE INDEX idx_qagroup_subscription_status    ON qa_group (stripe_subscription_status);

ALTER TABLE pending_signup
  ADD COLUMN status                     VARCHAR(24) NOT NULL DEFAULT 'PENDING_CHECKOUT',
  ADD COLUMN email_verification_token   VARCHAR(128) NULL,
  ADD COLUMN email_verification_expires_at TIMESTAMP NULL,
  ADD COLUMN stripe_checkout_session_id VARCHAR(128) NULL,
  ADD COLUMN stripe_customer_id         VARCHAR(64) NULL,
  ADD COLUMN stripe_subscription_id     VARCHAR(64) NULL;

CREATE UNIQUE INDEX ux_pending_signup_checkout_session
    ON pending_signup (stripe_checkout_session_id);

CREATE INDEX idx_pending_signup_status ON pending_signup (status);

ALTER TABLE qa_group
    ADD COLUMN access_state VARCHAR(24) NOT NULL DEFAULT 'PENDING';

CREATE INDEX idx_qagroup_access_state ON qa_group (access_state);