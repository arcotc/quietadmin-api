DROP INDEX uq_team_name_per_group ON team;

CREATE UNIQUE INDEX uq_team_name_per_group
    ON team (group_id, name, deleted_at);