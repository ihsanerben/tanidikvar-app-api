CREATE TABLE answers (
    id uuid PRIMARY KEY,
    question_id uuid NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
    author_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    answer_kind varchar(20) NOT NULL DEFAULT 'COMMUNITY',
    body varchar(5000) NOT NULL CHECK (length(trim(body)) BETWEEN 10 AND 5000),
    published_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    edited_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT chk_answers_kind CHECK (answer_kind='COMMUNITY'),
    UNIQUE(question_id,author_id,answer_kind)
);
CREATE INDEX idx_answers_question ON answers(question_id,answer_kind,published_at,id) WHERE deleted_at IS NULL;
CREATE INDEX idx_answers_author ON answers(author_id,published_at,id);
CREATE TRIGGER answers_no_delete BEFORE DELETE OR TRUNCATE ON answers
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
