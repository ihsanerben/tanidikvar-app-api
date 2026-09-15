ALTER TABLE universities
    ADD COLUMN institution_type varchar(20) NOT NULL DEFAULT 'BELIRTILMEMIS',
    ADD CONSTRAINT chk_universities_institution_type
        CHECK (institution_type IN ('DEVLET', 'VAKIF', 'BELIRTILMEMIS'));

CREATE INDEX idx_universities_public_filters
    ON universities (institution_type, city)
    WHERE deleted_at IS NULL;
