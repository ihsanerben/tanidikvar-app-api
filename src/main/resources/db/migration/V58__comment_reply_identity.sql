ALTER TABLE answer_comments ADD COLUMN reply_to_id uuid;
ALTER TABLE answer_comments ADD CONSTRAINT uq_comment_answer UNIQUE(id,answer_id);
ALTER TABLE answer_comments ADD CONSTRAINT fk_comment_reply_same_answer FOREIGN KEY(reply_to_id,answer_id) REFERENCES answer_comments(id,answer_id) ON DELETE RESTRICT;
ALTER TABLE answer_comments ADD CONSTRAINT chk_comment_not_self CHECK(reply_to_id IS DISTINCT FROM id);
CREATE INDEX idx_comment_reply ON answer_comments(reply_to_id) WHERE reply_to_id IS NOT NULL;
