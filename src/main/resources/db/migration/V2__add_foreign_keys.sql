CREATE INDEX idx_membership_user ON membership(user_id);
CREATE INDEX idx_membership_group ON membership(group_id);

CREATE INDEX idx_rota_group ON rota(group_id);
CREATE INDEX idx_rota_entry_rota ON rota_entry(rota_id);

CREATE INDEX idx_notice_group ON notice(group_id);
CREATE INDEX idx_resource_group ON resource(group_id);