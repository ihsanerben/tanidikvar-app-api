CREATE TABLE user_profiles (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
    first_name varchar(80) NOT NULL CHECK (length(trim(first_name)) > 0),
    last_name varchar(80) NOT NULL CHECK (length(trim(last_name)) > 0),
    education_status varchar(30) NOT NULL CHECK (education_status IN ('YKS_ADAYI','UNIVERSITE_OGRENCISI','MEZUN')),
    university_department_id uuid REFERENCES university_departments(id) ON DELETE RESTRICT,
    graduation_year integer CHECK (graduation_year BETWEEN 1900 AND 9999),
    biography varchar(1000),
    occupation varchar(120),
    company varchar(120),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0),
    CHECK ((education_status='YKS_ADAYI' AND university_department_id IS NULL AND graduation_year IS NULL)
        OR (education_status='UNIVERSITE_OGRENCISI' AND university_department_id IS NOT NULL AND graduation_year IS NULL)
        OR (education_status='MEZUN' AND university_department_id IS NOT NULL AND graduation_year IS NOT NULL))
);
CREATE INDEX idx_user_profiles_education ON user_profiles(university_department_id);
CREATE TABLE tags (
    id uuid PRIMARY KEY,
    name varchar(200) NOT NULL CHECK (length(trim(name)) > 0),
    normalized_name varchar(200) NOT NULL UNIQUE CHECK (length(trim(normalized_name)) > 0),
    created_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE TABLE management_actions (
    id uuid PRIMARY KEY,
    actor_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    action varchar(40) NOT NULL,
    target_type varchar(40) NOT NULL,
    target_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE INDEX idx_management_actions_target ON management_actions(target_type, target_id, occurred_at);
CREATE TRIGGER user_profiles_no_delete BEFORE DELETE OR TRUNCATE ON user_profiles
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER tags_no_delete BEFORE DELETE OR TRUNCATE ON tags
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER management_actions_no_delete BEFORE DELETE OR TRUNCATE ON management_actions
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
