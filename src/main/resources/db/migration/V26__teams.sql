-- ==========================================
-- V26__teams.sql
-- Structured team visibility for notices
-- Calm infrastructure — no social mechanics
-- ==========================================


-- ==========================================
-- TEAM
-- ==========================================

CREATE TABLE team
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id    BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NULL,
    deleted_at  TIMESTAMP NULL,

    CONSTRAINT fk_team_group
        FOREIGN KEY (group_id)
            REFERENCES qa_group (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_team_name_per_group
        UNIQUE (group_id, name)
);

-- Index to support group-scoped lookups
CREATE INDEX idx_team_group_id
    ON team (group_id);



-- ==========================================
-- TEAM MEMBERSHIP
-- ==========================================

CREATE TABLE team_membership
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id    BIGINT    NOT NULL,
    user_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tm_team
        FOREIGN KEY (team_id)
            REFERENCES team (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_tm_user
        FOREIGN KEY (user_id)
            REFERENCES user_account (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_team_user
        UNIQUE (team_id, user_id)
);

-- Indexes for visibility routing queries
CREATE INDEX idx_tm_team_id
    ON team_membership (team_id);

CREATE INDEX idx_tm_user_id
    ON team_membership (user_id);

-- Composite index for common visibility filter
CREATE INDEX idx_tm_user_team
    ON team_membership (user_id, team_id);



-- ==========================================
-- NOTICE TEAM VISIBILITY
-- ==========================================

CREATE TABLE notice_team_visibility
(
    id        BIGINT PRIMARY KEY AUTO_INCREMENT,
    notice_id BIGINT NOT NULL,
    team_id   BIGINT NOT NULL,

    CONSTRAINT fk_ntv_notice
        FOREIGN KEY (notice_id)
            REFERENCES notice (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_ntv_team
        FOREIGN KEY (team_id)
            REFERENCES team (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_notice_team
        UNIQUE (notice_id, team_id)
);

-- Indexes to optimise visibility resolution
CREATE INDEX idx_ntv_notice_id
    ON notice_team_visibility (notice_id);

CREATE INDEX idx_ntv_team_id
    ON notice_team_visibility (team_id);