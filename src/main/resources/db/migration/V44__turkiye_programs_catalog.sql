-- Switch the catalog upstream from the unavailable live YOK Atlas endpoint to
-- cngil/turkiye-university-programs programs.csv. Existing product UUIDs remain
-- stable; records absent from the new snapshot are soft-deleted by the importer.
ALTER TABLE catalog_sync_runs DROP CONSTRAINT catalog_sync_runs_source_check;
ALTER TABLE catalog_sync_runs
    ADD CONSTRAINT catalog_sync_runs_source_check
        CHECK (source IN ('YOK_ATLAS', 'TURKIYE_PROGRAMS'));

ALTER TABLE program_families DROP CONSTRAINT program_families_source_check;
ALTER TABLE program_families
    ADD CONSTRAINT program_families_source_check
        CHECK (source IN ('YOK_ATLAS', 'TURKIYE_PROGRAMS'));

ALTER TABLE universities DROP CONSTRAINT chk_universities_catalog_source;
ALTER TABLE universities
    ADD CONSTRAINT chk_universities_catalog_source
        CHECK (catalog_source IS NULL OR catalog_source IN ('YOK_ATLAS', 'TURKIYE_PROGRAMS', 'MANUAL'));

ALTER TABLE universities DROP CONSTRAINT chk_universities_source_identity;
ALTER TABLE universities
    ADD CONSTRAINT chk_universities_source_identity CHECK (
        (catalog_source IN ('YOK_ATLAS', 'TURKIYE_PROGRAMS') AND source_university_id IS NOT NULL)
        OR (catalog_source = 'MANUAL' AND source_university_id IS NULL)
        OR (catalog_source IS NULL AND source_university_id IS NULL)
    );

ALTER TABLE universities DROP CONSTRAINT chk_universities_institution_type;
ALTER TABLE universities
    ADD CONSTRAINT chk_universities_institution_type
        CHECK (institution_type IN ('DEVLET', 'VAKIF', 'KKTC', 'YURT_DISI', 'BELIRTILMEMIS'));

DROP INDEX uq_universities_yok_source_id;
CREATE UNIQUE INDEX uq_universities_catalog_source_id
    ON universities(catalog_source, source_university_id)
    WHERE catalog_source IN ('YOK_ATLAS', 'TURKIYE_PROGRAMS');

ALTER TABLE academic_units DROP CONSTRAINT academic_units_source_check;
ALTER TABLE academic_units
    ADD CONSTRAINT academic_units_source_check
        CHECK (source IN ('YOK_ATLAS', 'TURKIYE_PROGRAMS'));

ALTER TABLE admission_statistics
    ADD COLUMN maximum_score numeric(10,5) CHECK (maximum_score >= 0),
    ADD COLUMN placed_male integer CHECK (placed_male >= 0),
    ADD COLUMN placed_female integer CHECK (placed_female >= 0),
    ADD COLUMN average_secondary_score numeric(10,5) CHECK (average_secondary_score >= 0),
    ADD COLUMN total_preferences integer CHECK (total_preferences >= 0),
    ADD COLUMN demand_per_quota numeric(12,5) CHECK (demand_per_quota >= 0),
    ADD COLUMN average_preference_rank numeric(12,5) CHECK (average_preference_rank >= 0),
    ADD COLUMN quota_general integer CHECK (quota_general >= 0),
    ADD COLUMN quota_school_first integer CHECK (quota_school_first >= 0),
    ADD COLUMN quota_martyr_veteran integer CHECK (quota_martyr_veteran >= 0),
    ADD COLUMN quota_woman_34plus integer CHECK (quota_woman_34plus >= 0),
    ADD COLUMN quota_earthquake integer CHECK (quota_earthquake >= 0),
    ADD COLUMN statistics_source varchar(30);
