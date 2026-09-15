ALTER TABLE catalog_sync_runs
    ADD COLUMN operation varchar(20) NOT NULL DEFAULT 'APPLY'
        CHECK (operation IN ('PREVIEW', 'APPLY')),
    ADD COLUMN quality_report jsonb;

DROP INDEX uq_catalog_sync_successful_snapshot;

CREATE UNIQUE INDEX uq_catalog_sync_successful_snapshot
    ON catalog_sync_runs(source, snapshot_checksum)
    WHERE status = 'SUCCEEDED' AND operation = 'APPLY';

ALTER TABLE catalog_sync_runs
    ADD CONSTRAINT chk_catalog_sync_quality_report CHECK (
        (operation = 'PREVIEW' AND status = 'SUCCEEDED' AND quality_report IS NOT NULL)
        OR status <> 'SUCCEEDED'
        OR operation = 'APPLY'
    );
