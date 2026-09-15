ALTER TABLE education_verifications ADD COLUMN review_status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(review_status IN ('PENDING','APPROVED','REJECTED'));
ALTER TABLE education_verifications ADD COLUMN evidence varchar(2000);
ALTER TABLE education_verifications ADD COLUMN review_reason varchar(1000);
ALTER TABLE education_verifications ADD COLUMN reviewed_by uuid REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE education_verifications ADD COLUMN reviewed_at timestamptz;
UPDATE education_verifications SET review_status='APPROVED' WHERE verified_at IS NOT NULL;
CREATE INDEX idx_education_review_queue ON education_verifications(review_status,created_at) WHERE verification_type='MANAGER_REVIEW' AND deleted_at IS NULL;
