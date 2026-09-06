-- Explicit local maintenance only; never run automatically at application startup.
-- One atomic statement. For preview: BEGIN; run this file; ROLLBACK.
-- Only exact browser-test identities/names are targeted. No physical deletion.
DO $cleanup$
BEGIN
    SET LOCAL lock_timeout = '5s';
    LOCK TABLE users, user_profiles, auth_sessions, auth_action_tokens,
        universities, departments, university_departments, tags, questions,
        question_tags, answers, question_assignments, question_likes,
        question_views, stored_files, admin_applications, management_actions
        IN SHARE ROW EXCLUSIVE MODE;

    CREATE TEMP TABLE cleanup_users ON COMMIT DROP AS
        SELECT id FROM users WHERE email ~
        '^browser-(profile-)?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}@example[.]test$';
    CREATE TEMP TABLE cleanup_universities ON COMMIT DROP AS
        SELECT id FROM universities WHERE name ~ '^(Test|Başvuru) Üniversitesi [0-9a-f]{8}$';
    CREATE TEMP TABLE cleanup_departments ON COMMIT DROP AS
        SELECT id FROM departments WHERE name ~ '^(Test|Başvuru) Bölümü [0-9a-f]{8}$';
    CREATE TEMP TABLE cleanup_tags ON COMMIT DROP AS
        SELECT id FROM tags WHERE name ~ '^(Konu|Test Kampüs) [0-9a-f]{8}$'
        AND created_by IN (SELECT id FROM cleanup_users);
    CREATE TEMP TABLE cleanup_questions ON COMMIT DROP AS
        SELECT id FROM questions WHERE author_id IN (SELECT id FROM cleanup_users);
    CREATE TEMP TABLE cleanup_preserved_users ON COMMIT DROP AS
        SELECT * FROM users WHERE id NOT IN (SELECT id FROM cleanup_users);
    CREATE TEMP TABLE cleanup_preserved_profiles ON COMMIT DROP AS
        SELECT * FROM user_profiles WHERE user_id NOT IN (SELECT id FROM cleanup_users);

    RAISE NOTICE 'Matched test records: users=%, universities=%, departments=%, tags=%, questions=%',
        (SELECT count(*) FROM cleanup_users), (SELECT count(*) FROM cleanup_universities),
        (SELECT count(*) FROM cleanup_departments), (SELECT count(*) FROM cleanup_tags),
        (SELECT count(*) FROM cleanup_questions);

    UPDATE auth_sessions
        SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP), deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (user_id IN (SELECT id FROM cleanup_users));
    UPDATE auth_action_tokens
        SET consumed_at = COALESCE(consumed_at, CURRENT_TIMESTAMP), deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (user_id IN (SELECT id FROM cleanup_users));
    UPDATE question_tags
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (question_id IN (SELECT id FROM cleanup_questions) OR tag_id IN (SELECT id FROM cleanup_tags));
    UPDATE question_likes
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (question_id IN (SELECT id FROM cleanup_questions) OR user_id IN (SELECT id FROM cleanup_users));
    UPDATE question_views
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (question_id IN (SELECT id FROM cleanup_questions));
    UPDATE question_assignments
        SET cancelled_at = CURRENT_TIMESTAMP, deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (question_id IN (SELECT id FROM cleanup_questions) OR admin_id IN (SELECT id FROM cleanup_users));
    UPDATE answers
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (question_id IN (SELECT id FROM cleanup_questions) OR author_id IN (SELECT id FROM cleanup_users));
    UPDATE questions
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (id IN (SELECT id FROM cleanup_questions));
    UPDATE admin_applications
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (applicant_id IN (SELECT id FROM cleanup_users));
    UPDATE stored_files
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (owner_id IN (SELECT id FROM cleanup_users));
    UPDATE user_profiles
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (user_id IN (SELECT id FROM cleanup_users));
    UPDATE users
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (id IN (SELECT id FROM cleanup_users));
    UPDATE university_departments
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (university_id IN (SELECT id FROM cleanup_universities) OR department_id IN (SELECT id FROM cleanup_departments));
    UPDATE universities
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (id IN (SELECT id FROM cleanup_universities));
    UPDATE departments
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (id IN (SELECT id FROM cleanup_departments));
    UPDATE tags
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (id IN (SELECT id FROM cleanup_tags));
    UPDATE management_actions
        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE deleted_at IS NULL AND (actor_id IN (SELECT id FROM cleanup_users));

    IF EXISTS (
        SELECT 1 FROM cleanup_preserved_users old
        FULL JOIN (SELECT * FROM users WHERE id NOT IN (SELECT id FROM cleanup_users)) current USING (id)
        WHERE to_jsonb(old) IS DISTINCT FROM to_jsonb(current)
    ) OR EXISTS (
        SELECT 1 FROM cleanup_preserved_profiles old
        FULL JOIN (SELECT * FROM user_profiles WHERE user_id NOT IN (SELECT id FROM cleanup_users)) current USING (user_id)
        WHERE to_jsonb(old) IS DISTINCT FROM to_jsonb(current)
    ) THEN
        RAISE EXCEPTION 'Non-test account/profile changed; cleanup rolled back';
    END IF;
    RAISE NOTICE 'Non-test accounts and profiles unchanged. Active records: users=%, universities=%, departments=%, questions=%',
        (SELECT count(*) FROM users WHERE deleted_at IS NULL),
        (SELECT count(*) FROM universities WHERE deleted_at IS NULL),
        (SELECT count(*) FROM departments WHERE deleted_at IS NULL),
        (SELECT count(*) FROM questions WHERE deleted_at IS NULL);
END
$cleanup$;
