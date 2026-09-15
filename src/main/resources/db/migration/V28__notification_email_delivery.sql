CREATE TABLE outbox_deliveries (
 id uuid PRIMARY KEY,event_id uuid NOT NULL REFERENCES domain_outbox(id) ON DELETE RESTRICT,user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 due_at timestamptz NOT NULL,status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PROCESSING','SENT')),
 attempt_count integer NOT NULL DEFAULT 0,locked_at timestamptz,sent_at timestamptz,last_error varchar(1000),created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(event_id,user_id)
);
CREATE INDEX idx_outbox_deliveries_due ON outbox_deliveries(due_at,id) WHERE status='PENDING';
CREATE TRIGGER outbox_deliveries_no_delete BEFORE DELETE OR TRUNCATE ON outbox_deliveries FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
