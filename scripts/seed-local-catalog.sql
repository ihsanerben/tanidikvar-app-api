-- Manual reference data only; run with seed-local-catalog.sh after migrations.
-- Curated starter catalog, not an official popularity ranking.
-- Sources and scope: scripts/LOCAL_CATALOG.md. Existing catalog decisions are preserved.
INSERT INTO universities (id, name, normalized_name) VALUES
    ('e1fb7745-58af-5aa9-94dd-708dbc066de1', 'Boğaziçi Üniversitesi', 'boğaziçi üniversitesi'),
    ('138e29fd-2120-5862-8919-3eaa32f2cc5d', 'Orta Doğu Teknik Üniversitesi', 'orta doğu teknik üniversitesi'),
    ('ce5d2116-7149-5602-9a77-762898ff4dfb', 'İstanbul Teknik Üniversitesi', 'istanbul teknik üniversitesi'),
    ('a9c91b21-7775-50d8-90a1-be454933fa8d', 'Hacettepe Üniversitesi', 'hacettepe üniversitesi'),
    ('e4e45c2e-5922-5fba-8b7f-1c59c21af6e0', 'Ankara Üniversitesi', 'ankara üniversitesi'),
    ('4f4ad51c-934d-589a-8004-176b3b8e3ad5', 'İstanbul Üniversitesi', 'istanbul üniversitesi'),
    ('b1c6a396-990e-58b6-871e-fa84c262f8d6', 'Ege Üniversitesi', 'ege üniversitesi'),
    ('e2e946fe-189b-5446-a79a-be6809783101', 'Marmara Üniversitesi', 'marmara üniversitesi'),
    ('389043b7-8707-5a14-9151-67b4edb4075a', 'Yıldız Teknik Üniversitesi', 'yıldız teknik üniversitesi'),
    ('21af4207-a332-5499-84f7-fb13ffb0d223', 'Dokuz Eylül Üniversitesi', 'dokuz eylül üniversitesi')
ON CONFLICT (normalized_name) DO NOTHING;

INSERT INTO departments (id, name, normalized_name) VALUES
    ('357b081a-966a-595b-8f80-bf2bde0a7caa', 'Bilgisayar Mühendisliği', 'bilgisayar mühendisliği'),
    ('f67734b7-a88b-5b06-a0bb-e6ae7c05d421', 'Elektrik-Elektronik Mühendisliği', 'elektrik-elektronik mühendisliği'),
    ('1dc9260f-ccd1-5c15-b14e-1d8d7239ce8d', 'Endüstri Mühendisliği', 'endüstri mühendisliği'),
    ('b84fbf85-33cc-5130-a6d5-31f7c74644fd', 'İnşaat Mühendisliği', 'inşaat mühendisliği'),
    ('a34ed871-3fee-5cd0-a011-4497b4799db3', 'Makine Mühendisliği', 'makine mühendisliği'),
    ('13256d8e-9f2f-5e22-9248-c9502bb92bac', 'Mimarlık', 'mimarlık'),
    ('41c9b628-290f-5f96-967e-2c1ed62500e9', 'Tıp', 'tıp'),
    ('10c90124-d848-5c96-86e6-dec9e925867e', 'Hukuk', 'hukuk'),
    ('636a33de-f84f-5552-99f4-3b54077edc41', 'Psikoloji', 'psikoloji'),
    ('dad76c0d-3d43-5270-93fc-ea3525040de2', 'İşletme', 'işletme')
ON CONFLICT (normalized_name) DO NOTHING;

INSERT INTO university_departments (id, university_id, department_id)
SELECT gen_random_uuid(), u.id, d.id
FROM (VALUES
    ('boğaziçi üniversitesi', 'bilgisayar mühendisliği'),
    ('boğaziçi üniversitesi', 'elektrik-elektronik mühendisliği'),
    ('boğaziçi üniversitesi', 'endüstri mühendisliği'),
    ('boğaziçi üniversitesi', 'inşaat mühendisliği'),
    ('boğaziçi üniversitesi', 'makine mühendisliği'),
    ('boğaziçi üniversitesi', 'psikoloji'),
    ('boğaziçi üniversitesi', 'işletme'),
    ('orta doğu teknik üniversitesi', 'bilgisayar mühendisliği'),
    ('orta doğu teknik üniversitesi', 'elektrik-elektronik mühendisliği'),
    ('orta doğu teknik üniversitesi', 'endüstri mühendisliği'),
    ('orta doğu teknik üniversitesi', 'inşaat mühendisliği'),
    ('orta doğu teknik üniversitesi', 'makine mühendisliği'),
    ('orta doğu teknik üniversitesi', 'mimarlık'),
    ('orta doğu teknik üniversitesi', 'psikoloji'),
    ('orta doğu teknik üniversitesi', 'işletme'),
    ('istanbul teknik üniversitesi', 'bilgisayar mühendisliği'),
    ('istanbul teknik üniversitesi', 'endüstri mühendisliği'),
    ('istanbul teknik üniversitesi', 'inşaat mühendisliği'),
    ('istanbul teknik üniversitesi', 'makine mühendisliği'),
    ('istanbul teknik üniversitesi', 'mimarlık'),
    ('hacettepe üniversitesi', 'bilgisayar mühendisliği'),
    ('hacettepe üniversitesi', 'elektrik-elektronik mühendisliği'),
    ('hacettepe üniversitesi', 'endüstri mühendisliği'),
    ('hacettepe üniversitesi', 'inşaat mühendisliği'),
    ('hacettepe üniversitesi', 'makine mühendisliği'),
    ('hacettepe üniversitesi', 'tıp'),
    ('hacettepe üniversitesi', 'hukuk'),
    ('hacettepe üniversitesi', 'psikoloji'),
    ('hacettepe üniversitesi', 'işletme'),
    ('ankara üniversitesi', 'bilgisayar mühendisliği'),
    ('ankara üniversitesi', 'elektrik-elektronik mühendisliği'),
    ('ankara üniversitesi', 'inşaat mühendisliği'),
    ('ankara üniversitesi', 'makine mühendisliği'),
    ('ankara üniversitesi', 'tıp'),
    ('ankara üniversitesi', 'hukuk'),
    ('ankara üniversitesi', 'psikoloji'),
    ('ankara üniversitesi', 'işletme'),
    ('istanbul üniversitesi', 'bilgisayar mühendisliği'),
    ('istanbul üniversitesi', 'tıp'),
    ('istanbul üniversitesi', 'hukuk'),
    ('istanbul üniversitesi', 'psikoloji'),
    ('istanbul üniversitesi', 'işletme'),
    ('ege üniversitesi', 'bilgisayar mühendisliği'),
    ('ege üniversitesi', 'elektrik-elektronik mühendisliği'),
    ('ege üniversitesi', 'inşaat mühendisliği'),
    ('ege üniversitesi', 'makine mühendisliği'),
    ('ege üniversitesi', 'tıp'),
    ('ege üniversitesi', 'psikoloji'),
    ('ege üniversitesi', 'işletme'),
    ('marmara üniversitesi', 'bilgisayar mühendisliği'),
    ('marmara üniversitesi', 'elektrik-elektronik mühendisliği'),
    ('marmara üniversitesi', 'endüstri mühendisliği'),
    ('marmara üniversitesi', 'inşaat mühendisliği'),
    ('marmara üniversitesi', 'makine mühendisliği'),
    ('marmara üniversitesi', 'mimarlık'),
    ('marmara üniversitesi', 'tıp'),
    ('marmara üniversitesi', 'hukuk'),
    ('marmara üniversitesi', 'psikoloji'),
    ('marmara üniversitesi', 'işletme'),
    ('yıldız teknik üniversitesi', 'bilgisayar mühendisliği'),
    ('yıldız teknik üniversitesi', 'endüstri mühendisliği'),
    ('yıldız teknik üniversitesi', 'inşaat mühendisliği'),
    ('yıldız teknik üniversitesi', 'makine mühendisliği'),
    ('yıldız teknik üniversitesi', 'mimarlık'),
    ('yıldız teknik üniversitesi', 'işletme'),
    ('dokuz eylül üniversitesi', 'bilgisayar mühendisliği'),
    ('dokuz eylül üniversitesi', 'elektrik-elektronik mühendisliği'),
    ('dokuz eylül üniversitesi', 'endüstri mühendisliği'),
    ('dokuz eylül üniversitesi', 'inşaat mühendisliği'),
    ('dokuz eylül üniversitesi', 'makine mühendisliği'),
    ('dokuz eylül üniversitesi', 'mimarlık'),
    ('dokuz eylül üniversitesi', 'tıp'),
    ('dokuz eylül üniversitesi', 'hukuk'),
    ('dokuz eylül üniversitesi', 'psikoloji'),
    ('dokuz eylül üniversitesi', 'işletme')
) AS seed(university_name, department_name)
JOIN universities u ON u.normalized_name = seed.university_name
JOIN departments d ON d.normalized_name = seed.department_name
WHERE u.deleted_at IS NULL AND d.deleted_at IS NULL
ON CONFLICT (university_id, department_id) DO NOTHING;

INSERT INTO tags (id,name,normalized_name,created_by)
SELECT gen_random_uuid(), seed.name, seed.normalized_name, NULL
FROM (VALUES
 ('Kampüs','kampüs'),('Yurt','yurt'),('Ulaşım','ulaşım'),('Erasmus','erasmus'),
 ('Hazırlık','hazırlık'),('Dersler','dersler'),('Hocalar','hocalar'),('Staj','staj'),
 ('İş İmkanları','iş imkanları'),('Mezuniyet Sonrası','mezuniyet sonrası'),('Maaş','maaş'),
 ('Sosyal Hayat','sosyal hayat'),('Kulüpler','kulüpler'),('Yemekhane','yemekhane'),
 ('Burs','burs'),('Çift Anadal','çift anadal'),('Yandal','yandal'),
 ('Yatay Geçiş','yatay geçiş'),('Work & Travel','work & travel')
) AS seed(name,normalized_name)
ON CONFLICT (normalized_name) DO NOTHING;
