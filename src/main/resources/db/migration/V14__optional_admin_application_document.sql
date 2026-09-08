ALTER TABLE admin_applications ALTER COLUMN document_file_id DROP NOT NULL;
ALTER TABLE admin_applications ALTER COLUMN document_sha256 DROP NOT NULL;
ALTER TABLE admin_applications ADD CONSTRAINT ck_admin_application_document_pair
    CHECK ((document_file_id IS NULL) = (document_sha256 IS NULL));

COMMENT ON COLUMN admin_applications.document_file_id IS
    'Optional private verification document; applications may be submitted without a document.';
