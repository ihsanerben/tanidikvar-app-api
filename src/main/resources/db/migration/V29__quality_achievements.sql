CREATE TABLE user_achievements (
 id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,achievement_key varchar(80) NOT NULL,title varchar(120) NOT NULL,
 period_year integer,scope_type varchar(20),scope_id uuid,awarded_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
 UNIQUE NULLS NOT DISTINCT(user_id,achievement_key,period_year,scope_type,scope_id)
);
CREATE INDEX idx_user_achievements_profile ON user_achievements(user_id,awarded_at DESC);
CREATE TRIGGER user_achievements_no_delete BEFORE DELETE OR TRUNCATE ON user_achievements FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();

INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version,created_at) SELECT md5('experience:'||id)::uuid,author_id,'EXPERIENCE_CREATED',12,'EXPERIENCE',id,1,created_at FROM structured_experiences WHERE deleted_at IS NULL ON CONFLICT DO NOTHING;
INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version,created_at) SELECT md5('metric:'||id)::uuid,user_id,'METRIC_CONTRIBUTION',2,'CONTEXT_METRIC',id,1,created_at FROM context_metrics WHERE deleted_at IS NULL ON CONFLICT DO NOTHING;
INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version,created_at) SELECT md5('poll:'||id)::uuid,author_id,'POLL_CREATED',10,'POLL',id,1,created_at FROM polls WHERE deleted_at IS NULL ON CONFLICT DO NOTHING;

CREATE FUNCTION reward_experience_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES(md5('experience:'||NEW.id)::uuid,NEW.author_id,'EXPERIENCE_CREATED',12,'EXPERIENCE',NEW.id,1) ON CONFLICT DO NOTHING;
 IF NEW.template_type='NEW_STUDENT_GUIDE' THEN INSERT INTO user_achievements(id,user_id,achievement_key,title,period_year,scope_type,scope_id) VALUES(md5('new-student-guide:'||NEW.author_id||':'||extract(year from NEW.created_at)||':'||NEW.university_id)::uuid,NEW.author_id,'NEW_STUDENT_GUIDE','Yeni Kazananlar Rehberi',extract(year from NEW.created_at)::int,'UNIVERSITY',NEW.university_id) ON CONFLICT DO NOTHING;END IF;RETURN NEW;END $$;
CREATE FUNCTION reward_metric_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES(md5('metric:'||NEW.id)::uuid,NEW.user_id,'METRIC_CONTRIBUTION',2,'CONTEXT_METRIC',NEW.id,1) ON CONFLICT DO NOTHING;RETURN NEW;END $$;
CREATE FUNCTION reward_poll_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES(md5('poll:'||NEW.id)::uuid,NEW.author_id,'POLL_CREATED',10,'POLL',NEW.id,1) ON CONFLICT DO NOTHING;RETURN NEW;END $$;
CREATE TRIGGER structured_experiences_reward AFTER INSERT ON structured_experiences FOR EACH ROW EXECUTE FUNCTION reward_experience_insert();
CREATE TRIGGER context_metrics_reward AFTER INSERT ON context_metrics FOR EACH ROW EXECUTE FUNCTION reward_metric_insert();
CREATE TRIGGER polls_reward AFTER INSERT ON polls FOR EACH ROW EXECUTE FUNCTION reward_poll_insert();
