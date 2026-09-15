CREATE TABLE notification_preferences(user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,in_app_enabled boolean NOT NULL DEFAULT true,email_enabled boolean NOT NULL DEFAULT false,email_frequency varchar(20) NOT NULL DEFAULT 'DAILY' CHECK(email_frequency IN ('IMMEDIATE','DAILY','WEEKLY','NEVER')),question_routing_enabled boolean NOT NULL DEFAULT true,created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,version bigint NOT NULL DEFAULT 0);
CREATE TABLE domain_outbox(id uuid PRIMARY KEY,event_type varchar(60) NOT NULL,aggregate_type varchar(30) NOT NULL,aggregate_id uuid NOT NULL,payload jsonb NOT NULL,created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,processed_at timestamptz,attempt_count integer NOT NULL DEFAULT 0,last_error varchar(1000));
CREATE INDEX idx_domain_outbox_pending ON domain_outbox(created_at,id) WHERE processed_at IS NULL;
CREATE TRIGGER notification_preferences_no_delete BEFORE DELETE OR TRUNCATE ON notification_preferences FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TRIGGER domain_outbox_no_delete BEFORE DELETE OR TRUNCATE ON domain_outbox FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE FUNCTION route_new_question() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO domain_outbox(id,event_type,aggregate_type,aggregate_id,payload) VALUES(md5('question-published:'||NEW.id)::uuid,'QUESTION_PUBLISHED','QUESTION',NEW.id,jsonb_build_object('questionId',NEW.id,'scope',NEW.scope,'universityId',NEW.university_id,'programId',NEW.university_department_id)) ON CONFLICT DO NOTHING;
 INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id)
 SELECT md5('routed-question:'||NEW.id||':'||candidate.user_id)::uuid,candidate.user_id,'QUESTION_ROUTED','Deneyimine uygun yeni soru','Üniversite veya program deneyimine uygun yeni bir soru yayınlandı.','QUESTION',NEW.id
 FROM (SELECT DISTINCT f.user_id FROM follows f WHERE f.deleted_at IS NULL AND ((f.target_type='UNIVERSITY' AND f.target_id=NEW.university_id) OR (f.target_type='PROGRAM' AND f.target_id=NEW.university_department_id)) UNION SELECT ev.user_id FROM education_verifications ev WHERE ev.verified_at IS NOT NULL AND ev.deleted_at IS NULL AND ev.university_department_id=NEW.university_department_id) candidate
 LEFT JOIN notification_preferences np ON np.user_id=candidate.user_id
 WHERE candidate.user_id<>NEW.author_id AND coalesce(np.in_app_enabled,true) AND coalesce(np.question_routing_enabled,true)
 ON CONFLICT DO NOTHING;RETURN NEW;
END $$;
CREATE TRIGGER question_routing_after_insert AFTER INSERT ON questions FOR EACH ROW EXECUTE FUNCTION route_new_question();
