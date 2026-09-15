CREATE INDEX idx_admission_statistics_year_rank
    ON admission_statistics(guide_year, success_rank) WHERE success_rank IS NOT NULL;
CREATE INDEX idx_admission_statistics_year_score
    ON admission_statistics(guide_year, minimum_score) WHERE minimum_score IS NOT NULL;
CREATE INDEX idx_admission_options_score_duration
    ON admission_options(score_type, duration_years) WHERE deleted_at IS NULL;
CREATE INDEX idx_program_families_level
    ON program_families(degree_level) WHERE deleted_at IS NULL;
CREATE INDEX idx_academic_units_search_name
    ON academic_units(search_fold(name)) WHERE deleted_at IS NULL;
