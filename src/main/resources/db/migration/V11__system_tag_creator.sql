-- A null creator identifies curated system tags, without inventing a user account.
ALTER TABLE tags ALTER COLUMN created_by DROP NOT NULL;
COMMENT ON COLUMN tags.created_by IS 'Creating user; null for curated system catalog entries';
