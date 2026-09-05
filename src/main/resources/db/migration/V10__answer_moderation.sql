ALTER TABLE answers ADD COLUMN moderated_at timestamptz;
COMMENT ON COLUMN answers.moderated_at IS 'Manager soft deletion, independent of owner deleted_at';
