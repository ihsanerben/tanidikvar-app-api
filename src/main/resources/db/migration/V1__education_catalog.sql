-- Domain records are never physically deleted. Catalog APIs follow in the profile delivery.
CREATE TABLE universities (
    id uuid PRIMARY KEY,
    name varchar(200) NOT NULL CHECK (length(trim(name)) > 0),
    normalized_name varchar(200) NOT NULL UNIQUE CHECK (length(trim(normalized_name)) > 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE TABLE departments (
    id uuid PRIMARY KEY,
    name varchar(200) NOT NULL CHECK (length(trim(name)) > 0),
    normalized_name varchar(200) NOT NULL UNIQUE CHECK (length(trim(normalized_name)) > 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE TABLE university_departments (
    id uuid PRIMARY KEY,
    university_id uuid NOT NULL REFERENCES universities(id) ON DELETE RESTRICT,
    department_id uuid NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (university_id, department_id)
);
CREATE INDEX idx_university_departments_department ON university_departments(department_id, university_id);
CREATE FUNCTION reject_physical_delete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Physical deletion is disabled; use deleted_at' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER universities_no_delete BEFORE DELETE OR TRUNCATE ON universities
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER departments_no_delete BEFORE DELETE OR TRUNCATE ON departments
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER university_departments_no_delete BEFORE DELETE OR TRUNCATE ON university_departments
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
