ALTER TABLE content_reports DROP CONSTRAINT content_reports_target_type_check;
ALTER TABLE content_reports ADD CONSTRAINT content_reports_target_type_check CHECK(target_type IN ('ANSWER','ANSWER_COMMENT','EXPERIENCE'));
CREATE INDEX idx_experiences_template_scope ON structured_experiences(university_id,template_type,created_at DESC,id) WHERE deleted_at IS NULL;
-- Membership replaces the legacy verification-only vote restriction; votes are preserved.
UPDATE polls SET verified_only=false WHERE verified_only=true;
