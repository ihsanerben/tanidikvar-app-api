-- A sync run owns its staged rows. Nothing reaches the public catalog until the
-- complete upstream dataset has been downloaded and validated.
ALTER TABLE catalog_sync_runs
    ADD COLUMN heartbeat_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE catalog_sync_runs
SET status='FAILED',failure_reason='Uygulama yeniden başlatıldığı için senkronizasyon kesildi.',
    completed_at=CURRENT_TIMESTAMP
WHERE status='STARTED';

CREATE UNIQUE INDEX uq_catalog_sync_single_active_yok_run
    ON catalog_sync_runs(source)
    WHERE status='STARTED';

CREATE TABLE yok_catalog_program_stage (
    run_id uuid NOT NULL REFERENCES catalog_sync_runs(id) ON DELETE CASCADE,
    row_number integer NOT NULL CHECK (row_number >= 0),
    guide_code varchar(50) NOT NULL,
    payload jsonb NOT NULL,
    PRIMARY KEY (run_id,row_number),
    UNIQUE (run_id,guide_code)
);

CREATE TABLE yok_catalog_net_stage (
    run_id uuid NOT NULL REFERENCES catalog_sync_runs(id) ON DELETE CASCADE,
    row_number integer NOT NULL CHECK (row_number >= 0),
    guide_code varchar(50) NOT NULL,
    guide_year integer NOT NULL,
    payload jsonb NOT NULL,
    PRIMARY KEY (run_id,row_number),
    UNIQUE (run_id,guide_code,guide_year)
);

CREATE INDEX idx_yok_catalog_program_stage_run ON yok_catalog_program_stage(run_id,row_number);
CREATE INDEX idx_yok_catalog_net_stage_run ON yok_catalog_net_stage(run_id,row_number);
