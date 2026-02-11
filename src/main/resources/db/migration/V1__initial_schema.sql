-- ===============================
-- USERS
-- ===============================

CREATE TABLE user_account
(
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    platform_admin BOOLEAN   DEFAULT FALSE
);

-- ===============================
-- GROUPS
-- ===============================

CREATE TABLE qa_group
(
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    name                VARCHAR(255) NOT NULL,
    slug                VARCHAR(255) NOT NULL UNIQUE,
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    subscription_status VARCHAR(50) DEFAULT 'TRIAL',
    plan_type           VARCHAR(50) DEFAULT 'STANDARD',
    CONSTRAINT fk_group_creator
        FOREIGN KEY (created_by)
            REFERENCES user_account (id)
);

-- ===============================
-- MEMBERSHIPS (Many-to-many with roles)
-- ===============================

CREATE TABLE membership
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    group_id   BIGINT      NOT NULL,
    role       VARCHAR(50) NOT NULL, -- ADMIN / MEMBER
    invited_by BIGINT,
    joined_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_membership_user
        FOREIGN KEY (user_id)
            REFERENCES user_account (id),

    CONSTRAINT fk_membership_group
        FOREIGN KEY (group_id)
            REFERENCES qa_group (id),

    CONSTRAINT fk_membership_invited_by
        FOREIGN KEY (invited_by)
            REFERENCES user_account (id),

    CONSTRAINT uk_user_group UNIQUE (user_id, group_id)
);

-- ===============================
-- ROTAS
-- ===============================

CREATE TABLE rota
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id    BIGINT       NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    active      BOOLEAN   DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rota_group
        FOREIGN KEY (group_id)
            REFERENCES qa_group (id)
);

-- ===============================
-- ROTA ENTRIES
-- ===============================

CREATE TABLE rota_entry
(
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    rota_id          BIGINT       NOT NULL,
    role_name        VARCHAR(255) NOT NULL,
    rota_date        DATE         NOT NULL,
    assigned_user_id BIGINT,
    reminder_sent    BOOLEAN DEFAULT FALSE,

    CONSTRAINT fk_rota_entry_rota
        FOREIGN KEY (rota_id)
            REFERENCES rota (id),

    CONSTRAINT fk_rota_entry_user
        FOREIGN KEY (assigned_user_id)
            REFERENCES user_account (id)
);

-- ===============================
-- NOTICES
-- ===============================

CREATE TABLE notice
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id   BIGINT       NOT NULL,
    title      VARCHAR(255) NOT NULL,
    content    TEXT         NOT NULL,
    expires_at TIMESTAMP NULL,
    created_by BIGINT       NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notice_group
        FOREIGN KEY (group_id)
            REFERENCES qa_group (id),

    CONSTRAINT fk_notice_user
        FOREIGN KEY (created_by)
            REFERENCES user_account (id)
);

-- ===============================
-- RESOURCES
-- ===============================

CREATE TABLE resource
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id   BIGINT       NOT NULL,
    title      VARCHAR(255) NOT NULL,
    type       VARCHAR(50)  NOT NULL, -- LINK / FILE
    url        TEXT,
    file_path  TEXT,
    created_by BIGINT       NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_resource_group
        FOREIGN KEY (group_id)
            REFERENCES qa_group (id),

    CONSTRAINT fk_resource_user
        FOREIGN KEY (created_by)
            REFERENCES user_account (id)
);