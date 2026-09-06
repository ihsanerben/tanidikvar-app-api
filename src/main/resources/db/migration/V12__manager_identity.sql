-- Management identity is independent of the education profile.
CREATE TABLE manager_profiles (
 user_id uuid PRIMARY KEY REFERENCES users(id),
 first_name varchar(80) NOT NULL CHECK(length(trim(first_name))>0),
 last_name varchar(80) NOT NULL CHECK(length(trim(last_name))>0),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 deleted_at timestamptz,
 version bigint NOT NULL DEFAULT 1 CHECK(version>0)
);
INSERT INTO manager_profiles(user_id,first_name,last_name)
 SELECT u.id,p.first_name,p.last_name FROM users u JOIN user_profiles p ON p.user_id=u.id
 WHERE u.authority='MANAGER' AND p.deleted_at IS NULL;
CREATE TRIGGER manager_profiles_no_delete BEFORE DELETE OR TRUNCATE ON manager_profiles
 FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE INDEX idx_management_actions_filter ON management_actions(action,target_type,occurred_at DESC) WHERE deleted_at IS NULL;
