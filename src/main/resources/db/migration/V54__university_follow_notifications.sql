-- Product navigation now exposes university follows only. Historical rows remain
-- auditable but are hidden through the existing soft-delete contract.
UPDATE follows
SET deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP,version=version+1
WHERE target_type<>'UNIVERSITY' AND deleted_at IS NULL;

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
  WHERE f.target_type='UNIVERSITY' AND f.target_id=NEW.university_id AND f.deleted_at IS NULL
  UNION
  SELECT profile.user_id FROM user_profiles profile
  WHERE profile.university_id=NEW.university_id AND profile.deleted_at IS NULL
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
