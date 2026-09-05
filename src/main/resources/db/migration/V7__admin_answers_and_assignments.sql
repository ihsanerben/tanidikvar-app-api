ALTER TABLE answers DROP CONSTRAINT chk_answers_kind;
ALTER TABLE answers ADD COLUMN verification_application_id uuid;
ALTER TABLE answers ADD CONSTRAINT chk_answers_kind CHECK
 ((answer_kind='COMMUNITY' AND verification_application_id IS NULL) OR (answer_kind='ADMIN' AND verification_application_id IS NOT NULL));
ALTER TABLE answers ADD CONSTRAINT fk_answer_verification_owner FOREIGN KEY(verification_application_id,author_id) REFERENCES admin_applications(id,applicant_id);
CREATE TABLE question_assignments (
 id uuid PRIMARY KEY, question_id uuid NOT NULL REFERENCES questions(id), admin_id uuid NOT NULL REFERENCES users(id),
 assigned_at timestamptz NOT NULL DEFAULT clock_timestamp(), cancelled_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 deleted_at timestamptz, version bigint NOT NULL DEFAULT 1 CHECK(version>0),
 UNIQUE(question_id,admin_id), CHECK ((cancelled_at IS NULL)=(deleted_at IS NULL))
);
CREATE INDEX idx_assignments_admin ON question_assignments(admin_id,assigned_at DESC,id) WHERE deleted_at IS NULL;
CREATE TRIGGER assignments_no_delete BEFORE DELETE OR TRUNCATE ON question_assignments FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE FUNCTION protect_answer_verification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND ROW(NEW.answer_kind,NEW.author_id,NEW.question_id,NEW.verification_application_id,NEW.published_at)
 IS DISTINCT FROM ROW(OLD.answer_kind,OLD.author_id,OLD.question_id,OLD.verification_application_id,OLD.published_at)
 THEN RAISE EXCEPTION 'Answer publication identity is immutable' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' AND NEW.answer_kind='ADMIN' AND NOT EXISTS
 (SELECT 1 FROM admin_applications WHERE id=NEW.verification_application_id AND applicant_id=NEW.author_id AND status='APPROVED' AND deleted_at IS NULL)
 THEN RAISE EXCEPTION 'Approved verification required' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER answer_verification_guard BEFORE INSERT OR UPDATE ON answers FOR EACH ROW EXECUTE FUNCTION protect_answer_verification();

