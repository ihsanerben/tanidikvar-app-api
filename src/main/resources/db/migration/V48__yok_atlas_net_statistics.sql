ALTER TABLE admission_statistics
    ADD COLUMN score_coefficient numeric(6,4) CHECK (score_coefficient >= 0),
    ADD COLUMN tyt_turkish_net numeric(6,2),
    ADD COLUMN tyt_social_net numeric(6,2),
    ADD COLUMN tyt_math_net numeric(6,2),
    ADD COLUMN tyt_science_net numeric(6,2),
    ADD COLUMN ayt_math_net numeric(6,2),
    ADD COLUMN ayt_physics_net numeric(6,2),
    ADD COLUMN ayt_chemistry_net numeric(6,2),
    ADD COLUMN ayt_biology_net numeric(6,2),
    ADD COLUMN ayt_literature_net numeric(6,2),
    ADD COLUMN ayt_history1_net numeric(6,2),
    ADD COLUMN ayt_geography1_net numeric(6,2),
    ADD COLUMN ayt_history2_net numeric(6,2),
    ADD COLUMN ayt_geography2_net numeric(6,2),
    ADD COLUMN ayt_philosophy_net numeric(6,2),
    ADD COLUMN ayt_religion_net numeric(6,2),
    ADD COLUMN foreign_language_net numeric(6,2);

ALTER TABLE admission_options
    ADD COLUMN source_payload jsonb;
