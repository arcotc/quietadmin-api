ALTER TABLE stripe_event
    CHANGE stripe_event_id event_id VARCHAR(255) NOT NULL;

ALTER TABLE stripe_event
DROP INDEX uk_stripe_event_event_id;

ALTER TABLE stripe_event
    ADD CONSTRAINT uk_stripe_event_event_id UNIQUE (event_id);