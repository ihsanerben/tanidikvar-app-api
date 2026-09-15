CREATE TABLE follows (
 id uuid PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 target_type varchar(20) NOT NULL CHECK(target_type IN ('UNIVERSITY','PROGRAM','QUESTION','TANIDIK')),
 target_id uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted_at timestamptz,
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 UNIQUE(user_id,target_type,target_id)
);
CREATE INDEX idx_follows_user_visible ON follows(user_id,created_at DESC,id) WHERE deleted_at IS NULL;

CREATE TABLE saved_items (
 id uuid PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 target_type varchar(20) NOT NULL CHECK(target_type IN ('UNIVERSITY','PROGRAM','QUESTION','ANSWER')),
 target_id uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted_at timestamptz,
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 UNIQUE(user_id,target_type,target_id)
);
CREATE INDEX idx_saved_items_user_visible ON saved_items(user_id,created_at DESC,id) WHERE deleted_at IS NULL;

CREATE TABLE notifications (
 id uuid PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 notification_type varchar(40) NOT NULL,
 title varchar(200) NOT NULL CHECK(length(trim(title))>0),
 body varchar(1000) NOT NULL CHECK(length(trim(body))>0),
 target_type varchar(20),
 target_id uuid,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 read_at timestamptz,
 deleted_at timestamptz,
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 CHECK((target_type IS NULL)=(target_id IS NULL))
);
CREATE INDEX idx_notifications_inbox ON notifications(user_id,created_at DESC,id) WHERE deleted_at IS NULL;
CREATE INDEX idx_notifications_unread ON notifications(user_id,created_at DESC) WHERE deleted_at IS NULL AND read_at IS NULL;

CREATE TABLE point_events (
 id uuid PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 event_type varchar(40) NOT NULL,
 points integer NOT NULL CHECK(points BETWEEN -10000 AND 10000 AND points<>0),
 source_type varchar(30) NOT NULL,
 source_id uuid NOT NULL,
 policy_version integer NOT NULL CHECK(policy_version>0),
 reverses_event_id uuid REFERENCES point_events(id) ON DELETE RESTRICT,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(user_id,event_type,source_type,source_id,policy_version)
);
CREATE INDEX idx_point_events_user ON point_events(user_id,created_at DESC,id);

CREATE VIEW user_point_totals AS
 SELECT user_id,coalesce(sum(points),0)::bigint total_points,count(*)::bigint event_count,max(created_at) last_event_at
 FROM point_events GROUP BY user_id;

CREATE TRIGGER follows_no_delete BEFORE DELETE OR TRUNCATE ON follows FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER saved_items_no_delete BEFORE DELETE OR TRUNCATE ON saved_items FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER notifications_no_delete BEFORE DELETE OR TRUNCATE ON notifications FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER point_events_no_delete BEFORE DELETE OR TRUNCATE ON point_events FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
