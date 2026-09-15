-- YOK Atlas is an upstream source. Public/product records keep internal UUIDs so
-- upstream identifier changes never leak into profiles, questions or URLs.
ALTER TABLE universities
    ADD COLUMN catalog_source varchar(30),
    ADD COLUMN source_university_id bigint,
    ADD CONSTRAINT chk_universities_catalog_source
        CHECK (catalog_source IS NULL OR catalog_source IN ('YOK_ATLAS', 'MANUAL')),
    ADD CONSTRAINT chk_universities_source_identity
        CHECK (
            (catalog_source = 'YOK_ATLAS' AND source_university_id IS NOT NULL)
            OR (catalog_source = 'MANUAL' AND source_university_id IS NULL)
            OR (catalog_source IS NULL AND source_university_id IS NULL)
        );

CREATE UNIQUE INDEX uq_universities_yok_source_id
    ON universities(source_university_id)
    WHERE catalog_source = 'YOK_ATLAS';

CREATE TABLE catalog_sync_runs (
    id uuid PRIMARY KEY,
    source varchar(30) NOT NULL CHECK (source = 'YOK_ATLAS'),
    status varchar(20) NOT NULL CHECK (status IN ('STARTED', 'SUCCEEDED', 'SKIPPED', 'FAILED')),
    source_updated_at timestamptz,
    snapshot_checksum varchar(64) NOT NULL CHECK (snapshot_checksum ~ '^[0-9a-f]{64}$'),
    universities_seen integer NOT NULL DEFAULT 0 CHECK (universities_seen >= 0),
    programs_seen integer NOT NULL DEFAULT 0 CHECK (programs_seen >= 0),
    options_seen integer NOT NULL DEFAULT 0 CHECK (options_seen >= 0),
    started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamptz,
    failure_reason varchar(2000),
    created_by uuid REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_catalog_sync_run_completion CHECK (
        (status = 'STARTED' AND completed_at IS NULL AND failure_reason IS NULL)
        OR (status = 'SUCCEEDED' AND completed_at IS NOT NULL AND failure_reason IS NULL)
        OR (status = 'SKIPPED' AND completed_at IS NOT NULL AND failure_reason IS NULL)
        OR (status = 'FAILED' AND completed_at IS NOT NULL AND failure_reason IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_catalog_sync_successful_snapshot
    ON catalog_sync_runs(source, snapshot_checksum)
    WHERE status = 'SUCCEEDED';

CREATE TABLE program_families (
    id uuid PRIMARY KEY,
    source varchar(30) NOT NULL CHECK (source = 'YOK_ATLAS'),
    source_program_group_id bigint NOT NULL CHECK (source_program_group_id > 0),
    name varchar(240) NOT NULL CHECK (length(trim(name)) > 0),
    normalized_name varchar(240) NOT NULL CHECK (length(trim(normalized_name)) > 0),
    degree_level varchar(20) NOT NULL CHECK (degree_level IN ('LISANS', 'ONLISANS')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (source, source_program_group_id, degree_level)
);

CREATE INDEX idx_program_families_public_name
    ON program_families(normalized_name, id) WHERE deleted_at IS NULL;

CREATE TABLE academic_units (
    id uuid PRIMARY KEY,
    university_id uuid NOT NULL REFERENCES universities(id) ON DELETE RESTRICT,
    source varchar(30) NOT NULL CHECK (source = 'YOK_ATLAS'),
    source_unit_id bigint NOT NULL CHECK (source_unit_id > 0),
    name varchar(300) NOT NULL CHECK (length(trim(name)) > 0),
    unit_type varchar(100),
    city varchar(100),
    district varchar(100),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (university_id, source, source_unit_id)
);

CREATE INDEX idx_academic_units_university
    ON academic_units(university_id, name, id) WHERE deleted_at IS NULL;

CREATE TABLE programs (
    id uuid PRIMARY KEY,
    university_id uuid NOT NULL REFERENCES universities(id) ON DELETE RESTRICT,
    program_family_id uuid NOT NULL REFERENCES program_families(id) ON DELETE RESTRICT,
    display_name varchar(300) NOT NULL CHECK (length(trim(display_name)) > 0),
    normalized_name varchar(300) NOT NULL CHECK (length(trim(normalized_name)) > 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (university_id, program_family_id)
);

CREATE INDEX idx_programs_university_name
    ON programs(university_id, normalized_name, id) WHERE deleted_at IS NULL;

CREATE INDEX idx_programs_family
    ON programs(program_family_id, university_id) WHERE deleted_at IS NULL;

CREATE TABLE admission_options (
    id uuid PRIMARY KEY,
    program_id uuid NOT NULL REFERENCES programs(id) ON DELETE RESTRICT,
    academic_unit_id uuid REFERENCES academic_units(id) ON DELETE RESTRICT,
    source_program_id bigint CHECK (source_program_id > 0),
    osym_guide_id bigint CHECK (osym_guide_id > 0),
    guide_code varchar(40) NOT NULL UNIQUE CHECK (length(trim(guide_code)) > 0),
    score_type varchar(20),
    education_type varchar(100),
    language varchar(100),
    scholarship varchar(120),
    duration_years smallint CHECK (duration_years BETWEEN 1 AND 10),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE INDEX idx_admission_options_program
    ON admission_options(program_id, guide_code) WHERE deleted_at IS NULL;

CREATE TABLE admission_statistics (
    admission_option_id uuid NOT NULL REFERENCES admission_options(id) ON DELETE RESTRICT,
    guide_year smallint NOT NULL CHECK (guide_year BETWEEN 2000 AND 2200),
    quota integer CHECK (quota >= 0),
    placed integer CHECK (placed >= 0),
    minimum_score numeric(10,5) CHECK (minimum_score >= 0),
    success_rank integer CHECK (success_rank > 0),
    source_payload_checksum varchar(64) NOT NULL CHECK (source_payload_checksum ~ '^[0-9a-f]{64}$'),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (admission_option_id, guide_year)
);

ALTER TABLE university_departments
    ADD COLUMN program_id uuid REFERENCES programs(id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_university_departments_program
    ON university_departments(program_id) WHERE program_id IS NOT NULL;

CREATE TRIGGER catalog_sync_runs_no_delete BEFORE DELETE OR TRUNCATE ON catalog_sync_runs
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER program_families_no_delete BEFORE DELETE OR TRUNCATE ON program_families
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER academic_units_no_delete BEFORE DELETE OR TRUNCATE ON academic_units
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER programs_no_delete BEFORE DELETE OR TRUNCATE ON programs
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER admission_options_no_delete BEFORE DELETE OR TRUNCATE ON admission_options
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER admission_statistics_no_delete BEFORE DELETE OR TRUNCATE ON admission_statistics
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
