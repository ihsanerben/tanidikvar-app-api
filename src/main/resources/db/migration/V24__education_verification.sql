CREATE TABLE education_verifications (
 id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 verification_type varchar(20) NOT NULL CHECK(verification_type IN ('SCHOOL_EMAIL','MANAGER_REVIEW')),
 school_email varchar(254),code_hash varchar(64),expires_at timestamptz,verified_at timestamptz,
 university_department_id uuid REFERENCES university_departments(id) ON DELETE RESTRICT,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,deleted_at timestamptz,version bigint NOT NULL DEFAULT 0,
 CHECK((verification_type='SCHOOL_EMAIL' AND school_email IS NOT NULL AND code_hash IS NOT NULL AND expires_at IS NOT NULL) OR verification_type='MANAGER_REVIEW')
);
CREATE UNIQUE INDEX uq_education_verification_active ON education_verifications(user_id,verification_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_education_verifications_user ON education_verifications(user_id,created_at DESC);
CREATE TRIGGER education_verifications_no_delete BEFORE DELETE OR TRUNCATE ON education_verifications FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();

INSERT INTO education_verifications(id,user_id,verification_type,verified_at,university_department_id)
SELECT md5('legacy-education-verification:'||aa.id)::uuid,aa.applicant_id,'MANAGER_REVIEW',aa.reviewed_at,aa.university_department_id
FROM admin_applications aa WHERE aa.status='APPROVED' AND aa.deleted_at IS NULL ON CONFLICT DO NOTHING;
