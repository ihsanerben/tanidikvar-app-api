CREATE TABLE questions (
    id uuid PRIMARY KEY,
    author_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    request_id uuid NOT NULL,
    title varchar(200) NOT NULL CHECK (length(trim(title)) BETWEEN 10 AND 200),
    body varchar(5000),
    scope varchar(30) NOT NULL CHECK (scope IN ('GENERAL','UNIVERSITY','UNIVERSITY_DEPARTMENT')),
    university_id uuid REFERENCES universities(id) ON DELETE RESTRICT,
    university_department_id uuid REFERENCES university_departments(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edited_at timestamptz,
    archived_at timestamptz,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE(author_id,request_id),
    CHECK ((scope='GENERAL' AND university_id IS NULL AND university_department_id IS NULL)
        OR (scope='UNIVERSITY' AND university_id IS NOT NULL AND university_department_id IS NULL)
        OR (scope='UNIVERSITY_DEPARTMENT' AND university_id IS NULL AND university_department_id IS NOT NULL))
);
CREATE INDEX idx_questions_discovery ON questions(created_at DESC,id DESC) WHERE deleted_at IS NULL AND archived_at IS NULL;
CREATE INDEX idx_questions_author ON questions(author_id,created_at DESC,id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_questions_university ON questions(university_id,created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_questions_education ON questions(university_department_id,created_at DESC) WHERE deleted_at IS NULL;
CREATE TABLE question_tags (
    question_id uuid NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
    tag_id uuid NOT NULL REFERENCES tags(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY(question_id,tag_id)
);
CREATE INDEX idx_question_tags_tag ON question_tags(tag_id,question_id) WHERE deleted_at IS NULL;
CREATE TRIGGER questions_no_delete BEFORE DELETE OR TRUNCATE ON questions
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER question_tags_no_delete BEFORE DELETE OR TRUNCATE ON question_tags
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
