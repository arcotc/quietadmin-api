ALTER TABLE notice
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';

CREATE INDEX idx_notice_group_status_expires
    ON notice (group_id, status, expires_at);