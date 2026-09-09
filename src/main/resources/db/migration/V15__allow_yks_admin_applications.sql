ALTER TABLE admin_applications
 ALTER COLUMN university_department_id DROP NOT NULL,
 ALTER COLUMN university_name DROP NOT NULL,
 ALTER COLUMN department_name DROP NOT NULL;

DO $$
DECLARE previous_check record;
BEGIN
 FOR previous_check IN
  SELECT conname FROM pg_constraint
  WHERE conrelid='admin_applications'::regclass
    AND contype='c'
    AND pg_get_constraintdef(oid) LIKE '%education_status%'
 LOOP
  EXECUTE format('ALTER TABLE admin_applications DROP CONSTRAINT %I',previous_check.conname);
 END LOOP;
END $$;

ALTER TABLE admin_applications ADD CONSTRAINT ck_admin_application_education_snapshot CHECK (
 (education_status='YKS_ADAYI' AND university_department_id IS NULL AND university_name IS NULL AND department_name IS NULL AND graduation_year IS NULL)
 OR (education_status='UNIVERSITE_OGRENCISI' AND university_department_id IS NOT NULL AND university_name IS NOT NULL AND department_name IS NOT NULL AND graduation_year IS NULL)
 OR (education_status='MEZUN' AND university_department_id IS NOT NULL AND university_name IS NOT NULL AND department_name IS NOT NULL AND graduation_year BETWEEN 1900 AND 9999)
);
