CREATE TABLE users (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE CHECK (email = lower(trim(email)) AND length(email) > 3),
    password_hash varchar(100) NOT NULL,
    email_verified_at timestamptz,
    authority varchar(20) NOT NULL DEFAULT 'MEMBER' CHECK (authority IN ('MEMBER', 'ADMIN', 'MANAGER')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE TABLE auth_sessions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    family_id uuid NOT NULL,
    token_hash varchar(64) NOT NULL UNIQUE CHECK (length(token_hash) = 64),
    expires_at timestamptz NOT NULL,
    replaced_by_id uuid REFERENCES auth_sessions(id) ON DELETE RESTRICT,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (expires_at > created_at),
    CHECK (replaced_by_id IS NULL OR replaced_by_id <> id)
);
CREATE INDEX idx_auth_sessions_family ON auth_sessions(user_id, family_id);
CREATE UNIQUE INDEX uq_auth_sessions_active_family ON auth_sessions(family_id)
    WHERE replaced_by_id IS NULL AND revoked_at IS NULL AND deleted_at IS NULL;
CREATE TRIGGER users_no_delete BEFORE DELETE OR TRUNCATE ON users
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER auth_sessions_no_delete BEFORE DELETE OR TRUNCATE ON auth_sessions
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();

CREATE TABLE auth_action_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    purpose varchar(20) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash varchar(64) NOT NULL UNIQUE CHECK (length(token_hash) = 64),
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (expires_at > created_at)
);
CREATE INDEX idx_auth_action_tokens_user ON auth_action_tokens(user_id, purpose);
CREATE TRIGGER auth_action_tokens_no_delete BEFORE DELETE OR TRUNCATE ON auth_action_tokens
    FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
