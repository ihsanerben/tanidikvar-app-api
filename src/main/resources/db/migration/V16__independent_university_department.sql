ALTER TABLE user_profiles ADD COLUMN university_id uuid REFERENCES universities(id) ON DELETE RESTRICT;
ALTER TABLE user_profiles ADD COLUMN department_id uuid REFERENCES departments(id) ON DELETE RESTRICT;
ALTER TABLE questions ADD COLUMN department_id uuid REFERENCES departments(id) ON DELETE RESTRICT;
ALTER TABLE admin_applications ADD COLUMN university_id uuid REFERENCES universities(id) ON DELETE RESTRICT;
ALTER TABLE admin_applications ADD COLUMN department_id uuid REFERENCES departments(id) ON DELETE RESTRICT;

-- Eski kapsam kontrolleri, doğrudan kolonlar doldurulurken iki modeli aynı anda
-- geçerli saymadığı için veri dönüşümünden önce kaldırılır.
DO $$ DECLARE c record; BEGIN
 FOR c IN SELECT conrelid::regclass AS table_name,conname FROM pg_constraint WHERE contype='c' AND pg_get_constraintdef(oid) LIKE '%university_department_id%' AND conrelid IN ('user_profiles'::regclass,'questions'::regclass,'admin_applications'::regclass)
 LOOP EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',c.table_name,c.conname); END LOOP;
END $$;

UPDATE user_profiles p SET university_id=ud.university_id,department_id=ud.department_id FROM university_departments ud WHERE ud.id=p.university_department_id;
UPDATE questions q SET university_id=ud.university_id,department_id=ud.department_id FROM university_departments ud WHERE ud.id=q.university_department_id;
UPDATE admin_applications a SET university_id=ud.university_id,department_id=ud.department_id FROM university_departments ud WHERE ud.id=a.university_department_id;

CREATE FUNCTION populate_independent_education() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.university_department_id IS NOT NULL AND (NEW.university_id IS NULL OR NEW.department_id IS NULL) THEN
  SELECT university_id,department_id INTO NEW.university_id,NEW.department_id FROM university_departments WHERE id=NEW.university_department_id;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER user_profiles_independent_education BEFORE INSERT OR UPDATE ON user_profiles FOR EACH ROW EXECUTE FUNCTION populate_independent_education();
CREATE TRIGGER questions_independent_education BEFORE INSERT OR UPDATE ON questions FOR EACH ROW EXECUTE FUNCTION populate_independent_education();
CREATE TRIGGER applications_independent_education BEFORE INSERT OR UPDATE ON admin_applications FOR EACH ROW EXECUTE FUNCTION populate_independent_education();

ALTER TABLE user_profiles ADD CONSTRAINT user_profiles_independent_education_check CHECK (
 (education_status='YKS_ADAYI' AND university_id IS NULL AND department_id IS NULL AND graduation_year IS NULL) OR
 (education_status='UNIVERSITE_OGRENCISI' AND university_id IS NOT NULL AND department_id IS NOT NULL AND graduation_year IS NULL) OR
 (education_status='MEZUN' AND university_id IS NOT NULL AND department_id IS NOT NULL AND graduation_year IS NOT NULL));
ALTER TABLE questions ADD CONSTRAINT questions_independent_scope_check CHECK (
 (scope='GENERAL' AND university_id IS NULL AND department_id IS NULL) OR
 (scope='UNIVERSITY' AND university_id IS NOT NULL AND department_id IS NULL) OR
 (scope='UNIVERSITY_DEPARTMENT' AND university_id IS NOT NULL AND department_id IS NOT NULL));
ALTER TABLE admin_applications ADD CONSTRAINT applications_independent_education_check CHECK (
 (education_status='YKS_ADAYI' AND university_id IS NULL AND department_id IS NULL AND graduation_year IS NULL) OR
 (education_status='UNIVERSITE_OGRENCISI' AND university_id IS NOT NULL AND department_id IS NOT NULL AND graduation_year IS NULL) OR
 (education_status='MEZUN' AND university_id IS NOT NULL AND department_id IS NOT NULL AND graduation_year BETWEEN 1900 AND 9999));
CREATE INDEX idx_user_profiles_university_department ON user_profiles(university_id,department_id);
CREATE INDEX idx_questions_university_department ON questions(university_id,department_id,created_at DESC) WHERE deleted_at IS NULL;
