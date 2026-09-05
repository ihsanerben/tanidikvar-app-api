CREATE TABLE question_likes (
    question_id uuid NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    first_liked_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0),
    PRIMARY KEY(question_id,user_id)
);
CREATE INDEX idx_question_likes_active ON question_likes(question_id,first_liked_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_question_likes_period ON question_likes(first_liked_at,question_id) WHERE deleted_at IS NULL;
CREATE TABLE question_views (
    opening_event_id uuid PRIMARY KEY,
    question_id uuid NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
    viewed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0)
);
CREATE INDEX idx_question_views_active ON question_views(question_id,viewed_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_question_views_period ON question_views(viewed_at,question_id) WHERE deleted_at IS NULL;
CREATE TRIGGER question_likes_no_delete BEFORE DELETE OR TRUNCATE ON question_likes
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER question_views_no_delete BEFORE DELETE OR TRUNCATE ON question_views
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE FUNCTION protect_engagement_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.question_id IS DISTINCT FROM OLD.question_id OR
       (TG_TABLE_NAME='question_likes' AND (to_jsonb(NEW)->'user_id' IS DISTINCT FROM to_jsonb(OLD)->'user_id' OR to_jsonb(NEW)->'first_liked_at' IS DISTINCT FROM to_jsonb(OLD)->'first_liked_at')) OR
       (TG_TABLE_NAME='question_views' AND (to_jsonb(NEW)->'opening_event_id' IS DISTINCT FROM to_jsonb(OLD)->'opening_event_id' OR to_jsonb(NEW)->'viewed_at' IS DISTINCT FROM to_jsonb(OLD)->'viewed_at')) THEN
        RAISE EXCEPTION 'Engagement identity and first interaction time are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER question_likes_identity BEFORE UPDATE ON question_likes FOR EACH ROW EXECUTE FUNCTION protect_engagement_identity();
CREATE TRIGGER question_views_identity BEFORE UPDATE ON question_views FOR EACH ROW EXECUTE FUNCTION protect_engagement_identity();
