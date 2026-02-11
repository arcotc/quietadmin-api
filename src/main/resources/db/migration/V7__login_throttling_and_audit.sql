-- ===============================
-- LOGIN ATTEMPT THROTTLING
-- ===============================

CREATE TABLE login_throttle
(
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    email           VARCHAR(255) NOT NULL,
    ip_address      VARCHAR(64)  NOT NULL,
    failed_count    INT          NOT NULL DEFAULT 0,
    first_failed_at TIMESTAMP NULL,
    locked_until    TIMESTAMP NULL,
    updated_at      TIMESTAMP             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_login_throttle_email_ip (email, ip_address),
    KEY             ix_login_throttle_locked_until (locked_until)
);

-- ===============================
-- AUDIT LOG
-- ===============================

CREATE TABLE audit_event
(
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT NULL,
    group_id      BIGINT NULL,
    event_type    VARCHAR(64) NOT NULL,
    success       BOOLEAN     NOT NULL DEFAULT TRUE,
    message       VARCHAR(255) NULL,
    ip_address    VARCHAR(64) NULL,
    user_agent    VARCHAR(255) NULL,
    metadata_json JSON NULL,
    created_at    TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,

    KEY           ix_audit_event_user (user_id, created_at),
    KEY           ix_audit_event_group (group_id, created_at),
    KEY           ix_audit_event_type (event_type, created_at),

    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    CONSTRAINT fk_audit_group FOREIGN KEY (group_id) REFERENCES qa_group (id)
);