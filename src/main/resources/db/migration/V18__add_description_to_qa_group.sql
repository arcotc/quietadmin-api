-- =====================================================
-- Add description column to qa_group
-- =====================================================

ALTER TABLE qa_group
    ADD COLUMN description VARCHAR(500) NULL AFTER name;