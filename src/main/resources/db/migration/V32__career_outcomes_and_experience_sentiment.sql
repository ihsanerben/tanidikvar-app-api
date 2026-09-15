ALTER TABLE structured_experiences
    ADD COLUMN sentiment varchar(12) NOT NULL DEFAULT 'NEUTRAL'
    CHECK (sentiment IN ('POSITIVE', 'NEGATIVE', 'NEUTRAL'));

CREATE TABLE graduate_outcomes (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    university_id uuid NOT NULL REFERENCES universities(id) ON DELETE RESTRICT,
    university_department_id uuid NOT NULL REFERENCES university_departments(id) ON DELETE RESTRICT,
    sector varchar(120) NOT NULL CHECK (length(trim(sector)) >= 2),
    first_role varchar(120) NOT NULL CHECK (length(trim(first_role)) >= 2),
    company_type varchar(80) NOT NULL CHECK (length(trim(company_type)) >= 2),
    graduate_study boolean NOT NULL,
    job_search_months smallint NOT NULL CHECK (job_search_months BETWEEN 0 AND 120),
    verified boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (user_id, university_department_id)
);
CREATE INDEX idx_graduate_outcomes_scope ON graduate_outcomes(university_id, university_department_id) WHERE deleted_at IS NULL;
CREATE TRIGGER graduate_outcomes_no_delete BEFORE DELETE OR TRUNCATE ON graduate_outcomes FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
