CREATE TABLE evaluations (
 id uuid PRIMARY KEY,author_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 university_id uuid NOT NULL REFERENCES universities(id) ON DELETE RESTRICT,
 university_department_id uuid REFERENCES university_departments(id) ON DELETE RESTRICT,
 rating smallint NOT NULL CHECK(rating BETWEEN 1 AND 5),body varchar(2000),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted_at timestamptz,version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 UNIQUE NULLS NOT DISTINCT(author_id,university_id,university_department_id)
);
CREATE INDEX idx_evaluations_university ON evaluations(university_id,created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_evaluations_program ON evaluations(university_department_id,created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE polls (
 id uuid PRIMARY KEY,author_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 university_id uuid NOT NULL REFERENCES universities(id) ON DELETE RESTRICT,
 university_department_id uuid REFERENCES university_departments(id) ON DELETE RESTRICT,
 question varchar(300) NOT NULL CHECK(length(trim(question)) BETWEEN 10 AND 300),verified_only boolean NOT NULL DEFAULT false,
 closes_at timestamptz,created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted_at timestamptz,version bigint NOT NULL DEFAULT 0 CHECK(version>=0)
);
CREATE TABLE poll_options (
 id uuid PRIMARY KEY,poll_id uuid NOT NULL REFERENCES polls(id) ON DELETE RESTRICT,
 label varchar(120) NOT NULL CHECK(length(trim(label)) BETWEEN 1 AND 120),position smallint NOT NULL CHECK(position>=0),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,deleted_at timestamptz,UNIQUE(poll_id,position)
);
CREATE TABLE poll_votes (
 id uuid PRIMARY KEY,poll_id uuid NOT NULL REFERENCES polls(id) ON DELETE RESTRICT,option_id uuid NOT NULL REFERENCES poll_options(id) ON DELETE RESTRICT,
 voter_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,deleted_at timestamptz,version bigint NOT NULL DEFAULT 0 CHECK(version>=0),UNIQUE(poll_id,voter_id)
);
CREATE INDEX idx_polls_university ON polls(university_id,created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_polls_program ON polls(university_department_id,created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_poll_votes_results ON poll_votes(poll_id,option_id) WHERE deleted_at IS NULL;
CREATE TRIGGER evaluations_no_delete BEFORE DELETE OR TRUNCATE ON evaluations FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER polls_no_delete BEFORE DELETE OR TRUNCATE ON polls FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER poll_options_no_delete BEFORE DELETE OR TRUNCATE ON poll_options FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER poll_votes_no_delete BEFORE DELETE OR TRUNCATE ON poll_votes FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
