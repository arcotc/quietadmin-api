-- Shared Links (calm, structured, non-social)

CREATE TABLE IF NOT EXISTS shared_link_folder (
                                                  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                  group_id      BIGINT NOT NULL,
                                                  name          VARCHAR(120) NOT NULL,
    sort_order    INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_shared_link_folder_group
    FOREIGN KEY (group_id) REFERENCES qa_group(id)
                                                               ON DELETE CASCADE
    );

CREATE INDEX idx_slf_group_sort ON shared_link_folder(group_id, sort_order);
CREATE INDEX idx_slf_group_name ON shared_link_folder(group_id, name);


CREATE TABLE IF NOT EXISTS shared_link (
                                           id              BIGINT PRIMARY KEY AUTO_INCREMENT,
                                           group_id        BIGINT NOT NULL,
                                           folder_id       BIGINT NULL,

                                           title           VARCHAR(180) NOT NULL,
    url             VARCHAR(2048) NOT NULL,
    description     VARCHAR(500) NULL,

    sort_order      INT NOT NULL DEFAULT 0,
    is_pinned       TINYINT(1) NOT NULL DEFAULT 0,
    archived_at     TIMESTAMP NULL,

    created_by      BIGINT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_shared_link_group
    FOREIGN KEY (group_id) REFERENCES qa_group(id)
                                                                 ON DELETE CASCADE,

    CONSTRAINT fk_shared_link_folder
    FOREIGN KEY (folder_id) REFERENCES shared_link_folder(id)
                                                                 ON DELETE SET NULL,

    CONSTRAINT fk_shared_link_created_by
    FOREIGN KEY (created_by) REFERENCES user_account(id)
                                                                 ON DELETE SET NULL
    );

CREATE INDEX idx_sl_group_archived ON shared_link(group_id, archived_at);
CREATE INDEX idx_sl_group_folder_sort ON shared_link(group_id, folder_id, sort_order);
CREATE INDEX idx_sl_group_pinned ON shared_link(group_id, is_pinned);