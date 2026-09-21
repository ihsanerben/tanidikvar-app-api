CREATE TABLE content_reports (
    id uuid PRIMARY KEY,
    target_type varchar(30) NOT NULL CHECK (target_type IN ('ANSWER','ANSWER_COMMENT')),
    target_id uuid NOT NULL,
    reporter_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reason varchar(1000) NOT NULL CHECK (length(trim(reason)) BETWEEN 10 AND 1000),
    status varchar(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED','DISMISSED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    reviewed_by uuid REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_at timestamptz,
    resolution_reason varchar(1000),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (reporter_id,target_type,target_id)
);
CREATE INDEX idx_content_reports_review ON content_reports(status,created_at DESC,id);
CREATE TRIGGER content_reports_no_delete BEFORE DELETE OR TRUNCATE ON content_reports FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
