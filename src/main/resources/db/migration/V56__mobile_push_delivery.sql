CREATE TABLE push_devices (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 family_id uuid NOT NULL UNIQUE, push_token varchar(250) UNIQUE,
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 platform varchar(7) NOT NULL CHECK(platform IN ('IOS','ANDROID')),
 updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(push_token IS NULL OR push_token ~ '^(ExponentPushToken|ExpoPushToken)\[[A-Za-z0-9_-]+\]$')
);
CREATE INDEX idx_push_devices_user ON push_devices(user_id) WHERE push_token IS NOT NULL;
CREATE TABLE push_deliveries (
 id uuid PRIMARY KEY, notification_id uuid NOT NULL REFERENCES notifications(id) ON DELETE RESTRICT,
 device_id uuid NOT NULL REFERENCES push_devices(id) ON DELETE RESTRICT,
 device_version bigint NOT NULL CHECK(device_version>=0),
 status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PROCESSING','TICKET','RECEIPT','SENT','FAILED','CANCELLED')),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0),
 due_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, locked_at timestamptz,
 ticket_id varchar(100), last_error varchar(50), created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(notification_id,device_id)
);
CREATE INDEX idx_push_delivery_due ON push_deliveries(due_at,id) WHERE status IN ('PENDING','TICKET','PROCESSING','RECEIPT');
CREATE TRIGGER push_devices_no_delete BEFORE DELETE OR TRUNCATE ON push_devices FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER push_deliveries_no_delete BEFORE DELETE OR TRUNCATE ON push_deliveries FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE FUNCTION enqueue_mobile_push() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO push_deliveries(id,notification_id,device_id,device_version)
 SELECT md5(NEW.id::text||':'||d.id::text)::uuid,NEW.id,d.id,d.version
 FROM push_devices d LEFT JOIN notification_preferences np ON np.user_id=d.user_id
 WHERE d.user_id=NEW.user_id AND d.push_token IS NOT NULL AND coalesce(np.in_app_enabled,true)
 AND EXISTS(SELECT 1 FROM auth_sessions s WHERE s.user_id=d.user_id AND s.family_id=d.family_id
 AND s.replaced_by_id IS NULL AND s.revoked_at IS NULL AND s.deleted_at IS NULL AND s.expires_at>CURRENT_TIMESTAMP)
 ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;
CREATE TRIGGER notification_mobile_push AFTER INSERT ON notifications FOR EACH ROW EXECUTE FUNCTION enqueue_mobile_push();

-- Deferred until commit so the temporary retirement during refresh does not disable push.
CREATE FUNCTION disable_revoked_push_device() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM auth_sessions WHERE family_id=NEW.family_id AND user_id=NEW.user_id
 AND replaced_by_id IS NULL AND revoked_at IS NULL AND deleted_at IS NULL AND expires_at>CURRENT_TIMESTAMP) THEN
 UPDATE push_devices SET push_token=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE family_id=NEW.family_id AND user_id=NEW.user_id;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER session_push_revocation AFTER UPDATE ON auth_sessions
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION disable_revoked_push_device();
