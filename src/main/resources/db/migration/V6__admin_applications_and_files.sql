CREATE TABLE stored_files (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES users(id),
 purpose varchar(20) NOT NULL CHECK (purpose IN ('AVATAR','VERIFICATION')),
 storage_key varchar(80) NOT NULL UNIQUE,
 original_name varchar(255) NOT NULL, content_type varchar(80) NOT NULL,
 byte_size bigint NOT NULL CHECK (byte_size > 0),
 upload_status varchar(20) NOT NULL CHECK (upload_status IN ('UPLOADING','READY','FAILED')),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 deleted_at timestamptz, version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 UNIQUE(id,owner_id)
);
CREATE INDEX idx_files_owner_created ON stored_files(owner_id,created_at);
CREATE UNIQUE INDEX uq_active_avatar ON stored_files(owner_id) WHERE purpose='AVATAR' AND upload_status='READY' AND deleted_at IS NULL;
CREATE TABLE admin_applications (
 id uuid PRIMARY KEY, applicant_id uuid NOT NULL REFERENCES users(id), request_id uuid NOT NULL,
 submitted_first_name varchar(80) NOT NULL, submitted_last_name varchar(80) NOT NULL,
 education_status varchar(30) NOT NULL CHECK(education_status IN ('UNIVERSITE_OGRENCISI','MEZUN')),
 university_department_id uuid NOT NULL REFERENCES university_departments(id),
 university_name varchar(200) NOT NULL, department_name varchar(200) NOT NULL,
 graduation_year integer, occupation varchar(120), company varchar(120),
 document_file_id uuid NOT NULL UNIQUE, document_sha256 varchar(64) NOT NULL, profile_version bigint NOT NULL,
 status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 submitted_at timestamptz NOT NULL DEFAULT clock_timestamp(), reviewed_by uuid REFERENCES users(id),
 reviewed_at timestamptz, rejection_reason varchar(1000),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 deleted_at timestamptz, version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 UNIQUE(applicant_id,request_id), UNIQUE(id,applicant_id),
 FOREIGN KEY(document_file_id,applicant_id) REFERENCES stored_files(id,owner_id),
 CHECK ((education_status='MEZUN' AND graduation_year IS NOT NULL AND graduation_year BETWEEN 1900 AND 9999) OR (education_status='UNIVERSITE_OGRENCISI' AND graduation_year IS NULL)),
 CHECK ((status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND rejection_reason IS NULL)
 OR (status='APPROVED' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND rejection_reason IS NULL)
 OR (status='REJECTED' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND rejection_reason IS NOT NULL AND length(trim(rejection_reason))>0))
);
CREATE UNIQUE INDEX uq_pending_application ON admin_applications(applicant_id) WHERE status='PENDING' AND deleted_at IS NULL;
CREATE INDEX idx_applications_review ON admin_applications(status,submitted_at DESC,id);
ALTER TABLE users ADD COLUMN active_verification_application_id uuid;
ALTER TABLE users ADD CONSTRAINT fk_active_verification_owner FOREIGN KEY(active_verification_application_id,id) REFERENCES admin_applications(id,applicant_id);
ALTER TABLE management_actions ADD COLUMN reason varchar(1000);
CREATE TRIGGER stored_files_no_delete BEFORE DELETE OR TRUNCATE ON stored_files FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER admin_applications_no_delete BEFORE DELETE OR TRUNCATE ON admin_applications FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE FUNCTION protect_application_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.applicant_id,NEW.request_id,NEW.submitted_first_name,NEW.submitted_last_name,NEW.education_status,NEW.university_department_id,NEW.university_name,NEW.department_name,NEW.graduation_year,NEW.occupation,NEW.company,NEW.document_file_id,NEW.document_sha256,NEW.profile_version,NEW.submitted_at)
 IS DISTINCT FROM ROW(OLD.applicant_id,OLD.request_id,OLD.submitted_first_name,OLD.submitted_last_name,OLD.education_status,OLD.university_department_id,OLD.university_name,OLD.department_name,OLD.graduation_year,OLD.occupation,OLD.company,OLD.document_file_id,OLD.document_sha256,OLD.profile_version,OLD.submitted_at)
 OR (OLD.status<>'PENDING' AND ROW(NEW.status,NEW.reviewed_by,NEW.reviewed_at,NEW.rejection_reason) IS DISTINCT FROM ROW(OLD.status,OLD.reviewed_by,OLD.reviewed_at,OLD.rejection_reason))
 THEN RAISE EXCEPTION 'Application snapshot and completed decisions are immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER application_snapshot_immutable BEFORE UPDATE ON admin_applications FOR EACH ROW EXECUTE FUNCTION protect_application_snapshot();

