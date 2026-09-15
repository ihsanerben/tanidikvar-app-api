ALTER TABLE user_profiles ADD COLUMN class_year integer;

ALTER TABLE user_profiles ADD CONSTRAINT user_profiles_class_year_check CHECK (
 (education_status='UNIVERSITE_OGRENCISI' AND (class_year IS NULL OR class_year BETWEEN 1 AND 8)) OR
 (education_status<>'UNIVERSITE_OGRENCISI' AND class_year IS NULL)
);
