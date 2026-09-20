CREATE TABLE yok_atlas_net_statistics (
    guide_code varchar(64) NOT NULL,
    guide_year integer NOT NULL CHECK (guide_year BETWEEN 2015 AND 2100),
    minimum_score numeric(12,5),
    average_secondary_score numeric(12,5),
    score_coefficient numeric(6,4),
    tyt_turkish_net numeric(6,2),
    tyt_social_net numeric(6,2),
    tyt_math_net numeric(6,2),
    tyt_science_net numeric(6,2),
    ayt_math_net numeric(6,2),
    ayt_physics_net numeric(6,2),
    ayt_chemistry_net numeric(6,2),
    ayt_biology_net numeric(6,2),
    ayt_literature_net numeric(6,2),
    ayt_history1_net numeric(6,2),
    ayt_geography1_net numeric(6,2),
    ayt_history2_net numeric(6,2),
    ayt_geography2_net numeric(6,2),
    ayt_philosophy_net numeric(6,2),
    ayt_religion_net numeric(6,2),
    foreign_language_net numeric(6,2),
    source_payload jsonb NOT NULL,
    source_payload_checksum varchar(64) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (guide_code,guide_year)
);

CREATE INDEX ix_yok_atlas_net_statistics_year ON yok_atlas_net_statistics(guide_year);
