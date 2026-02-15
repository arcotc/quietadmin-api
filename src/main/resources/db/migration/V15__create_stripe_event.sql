CREATE TABLE stripe_event
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,

    stripe_event_id VARCHAR(255) NOT NULL,
    type            VARCHAR(255) NOT NULL,

    received_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    processed_at    TIMESTAMP NULL,
    success         BOOLEAN      NOT NULL DEFAULT FALSE,

    payload         JSON         NOT NULL,

    UNIQUE KEY uk_stripe_event_event_id (stripe_event_id),
    INDEX           ix_stripe_event_received_at (received_at)
);