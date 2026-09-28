ALTER TABLE notification_preferences ADD COLUMN categories jsonb NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(categories)='object');

CREATE FUNCTION notification_category_enabled(recipient uuid, category text) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT coalesce((SELECT coalesce((categories->>category)::boolean,true) FROM notification_preferences WHERE user_id=recipient),true)
$$;
CREATE FUNCTION filter_notification_preferences() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE category text;
BEGIN
 category=CASE
  WHEN NEW.notification_type='QUESTION_ROUTED' THEN 'QUESTION'
  WHEN NEW.notification_type='NEW_POLL' THEN 'POLL'
  WHEN NEW.notification_type='NEW_EVALUATION' THEN 'EVALUATION'
  WHEN NEW.notification_type='NEW_EXPERIENCE' THEN 'EXPERIENCE'
  WHEN NEW.notification_type='NEW_METRIC' THEN 'METRIC'
  WHEN NEW.notification_type='ANSWER_DISCUSSION' THEN 'REPLY'
  WHEN NEW.notification_type IN ('ACHIEVEMENT','TITLE_UPGRADED') THEN 'ACHIEVEMENT'
  WHEN NEW.notification_type IN ('FOLLOWED_QUESTION_ANSWERED','FOLLOWED_TANIDIK_ANSWERED','QUESTION_ANSWERED','HELPFUL_VOTE','BEST_ANSWER') THEN 'ANSWER'
  ELSE 'ACCOUNT' END;
 IF NOT coalesce((SELECT in_app_enabled FROM notification_preferences WHERE user_id=NEW.user_id),true)
    OR NOT notification_category_enabled(NEW.user_id,category) THEN RETURN NULL; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER notifications_preferences BEFORE INSERT ON notifications FOR EACH ROW EXECUTE FUNCTION filter_notification_preferences();

CREATE OR REPLACE FUNCTION notify_question_followers() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id)
 SELECT md5('question-answer:'||NEW.id||':'||recipient.user_id)::uuid,recipient.user_id,'QUESTION_ANSWERED','Soruya yeni yorum geldi','İlgilendiğin soruya yeni bir yorum eklendi.','ANSWER',NEW.id
 FROM (
  SELECT q.author_id user_id FROM questions q WHERE q.id=NEW.question_id
  UNION
  SELECT f.user_id FROM follows f JOIN questions q ON q.id=NEW.question_id
  WHERE f.deleted_at IS NULL AND ((f.target_type='UNIVERSITY' AND f.target_id=q.university_id AND notification_category_enabled(f.user_id,'UNIVERSITY')) OR (f.target_type='PROGRAM' AND f.target_id=q.program_id AND notification_category_enabled(f.user_id,'PROGRAM')))
 ) recipient WHERE recipient.user_id<>NEW.author_id ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION notify_answer_discussion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id)
 SELECT md5('answer-comment:'||NEW.id||':'||recipient.user_id)::uuid,recipient.user_id,'ANSWER_DISCUSSION','Yorumuna yanıt geldi','Tartışmada sana yeni bir yanıt yazıldı.','ANSWER_COMMENT',NEW.id
 FROM (
  SELECT author_id user_id FROM answer_comments WHERE id=NEW.reply_to_id
  UNION SELECT author_id FROM answers WHERE id=NEW.answer_id AND NEW.reply_to_id IS NULL
 ) recipient WHERE recipient.user_id<>NEW.author_id ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION notify_context_followers() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE university uuid; program uuid; author uuid; kind text; heading text; target text;
BEGIN
 university=NEW.university_id;program=NEW.program_id;
 IF TG_TABLE_NAME='context_metrics' THEN author=NEW.user_id;kind='NEW_METRIC';heading='Yeni gerçek hayat ölçümü';target='METRIC';
 ELSIF TG_TABLE_NAME='polls' THEN author=NEW.author_id;kind='NEW_POLL';heading='Yeni anket';target='POLL';
 ELSIF TG_TABLE_NAME='evaluations' THEN author=NEW.author_id;kind='NEW_EVALUATION';heading='Yeni değerlendirme';target='EVALUATION';
 ELSE author=NEW.author_id;kind='NEW_EXPERIENCE';heading='Yeni deneyim';target='EXPERIENCE'; END IF;
 IF program IS NULL AND NEW.university_department_id IS NOT NULL THEN SELECT ud.program_id INTO program FROM university_departments ud WHERE ud.id=NEW.university_department_id; END IF;
 INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id)
 SELECT DISTINCT md5(TG_TABLE_NAME||':'||NEW.id||':'||f.user_id)::uuid,f.user_id,kind,heading,'Takip ettiğin toplulukta yeni bir katkı paylaşıldı.',target,NEW.id
 FROM follows f WHERE f.deleted_at IS NULL AND f.user_id<>author
 AND ((f.target_type='UNIVERSITY' AND f.target_id=university AND notification_category_enabled(f.user_id,'UNIVERSITY')) OR (f.target_type='PROGRAM' AND f.target_id=program AND notification_category_enabled(f.user_id,'PROGRAM'))) ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;
CREATE TRIGGER metrics_activity_notification AFTER INSERT ON context_metrics FOR EACH ROW EXECUTE FUNCTION notify_context_followers();
CREATE OR REPLACE FUNCTION notify_achievement_awarded() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id) VALUES(md5('achievement-note:'||NEW.id)::uuid,NEW.user_id,'ACHIEVEMENT','Yeni rozet kazandın',NEW.title,'ACHIEVEMENT',NEW.id) ON CONFLICT DO NOTHING;RETURN NEW;END $$;

CREATE OR REPLACE FUNCTION route_new_question() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
 normalized_program_id uuid := NEW.program_id;
 legacy_university_department_id uuid;
BEGIN
 IF normalized_program_id IS NULL THEN
  SELECT ud.program_id,ud.id INTO normalized_program_id,legacy_university_department_id
  FROM university_departments ud
  WHERE ud.university_id=NEW.university_id AND ud.department_id=NEW.department_id AND ud.deleted_at IS NULL
  ORDER BY ud.created_at LIMIT 1;
 ELSE
  SELECT ud.id INTO legacy_university_department_id FROM university_departments ud
  WHERE ud.program_id=normalized_program_id AND ud.deleted_at IS NULL ORDER BY ud.created_at LIMIT 1;
 END IF;

 INSERT INTO domain_outbox(id,event_type,aggregate_type,aggregate_id,payload)
 VALUES(md5('question-published:'||NEW.id)::uuid,'QUESTION_PUBLISHED','QUESTION',NEW.id,
        jsonb_build_object('questionId',NEW.id,'scope',NEW.scope,'universityId',NEW.university_id,'programId',normalized_program_id))
 ON CONFLICT DO NOTHING;

 INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id)
 SELECT md5('routed-question:'||NEW.id||':'||candidate.user_id)::uuid,candidate.user_id,
        'QUESTION_ROUTED','Üniversitene yeni soru geldi',
        'Üniversitene ait yeni bir soru yayınlandı.','QUESTION',NEW.id
 FROM (
  SELECT DISTINCT f.user_id FROM follows f
  WHERE f.deleted_at IS NULL AND ((f.target_type='UNIVERSITY' AND f.target_id=NEW.university_id AND notification_category_enabled(f.user_id,'UNIVERSITY')) OR (NEW.scope='UNIVERSITY_DEPARTMENT' AND f.target_type='PROGRAM' AND f.target_id=normalized_program_id AND notification_category_enabled(f.user_id,'PROGRAM')))
  UNION
  SELECT profile.user_id FROM user_profiles profile
  WHERE profile.university_id=NEW.university_id AND profile.deleted_at IS NULL AND notification_category_enabled(profile.user_id,'UNIVERSITY')
  UNION
  SELECT ev.user_id FROM education_verifications ev
  WHERE ev.verified_at IS NOT NULL AND ev.deleted_at IS NULL
    AND (ev.program_id=normalized_program_id OR (ev.program_id IS NULL AND ev.university_department_id=legacy_university_department_id))
 ) candidate
 LEFT JOIN notification_preferences np ON np.user_id=candidate.user_id
 WHERE candidate.user_id<>NEW.author_id AND coalesce(np.in_app_enabled,true) AND coalesce(np.question_routing_enabled,true)
 ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;
