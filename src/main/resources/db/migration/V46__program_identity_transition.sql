-- Normalize every new program relationship while retaining legacy columns for old content.
ALTER TABLE user_profiles ADD COLUMN program_id uuid REFERENCES programs(id);
ALTER TABLE questions ADD COLUMN program_id uuid REFERENCES programs(id);
ALTER TABLE education_verifications ADD COLUMN program_id uuid REFERENCES programs(id);
ALTER TABLE evaluations ADD COLUMN program_id uuid REFERENCES programs(id);
ALTER TABLE polls ADD COLUMN program_id uuid REFERENCES programs(id);
ALTER TABLE context_metrics ADD COLUMN program_id uuid REFERENCES programs(id);
ALTER TABLE structured_experiences ADD COLUMN program_id uuid REFERENCES programs(id);
ALTER TABLE graduate_outcomes ADD COLUMN program_id uuid REFERENCES programs(id);

UPDATE user_profiles p SET program_id=ud.program_id
FROM university_departments ud
WHERE p.program_id IS NULL AND ud.university_id=p.university_id AND ud.department_id=p.department_id AND ud.program_id IS NOT NULL;
UPDATE questions q SET program_id=ud.program_id
FROM university_departments ud
WHERE q.program_id IS NULL AND ud.university_id=q.university_id AND ud.department_id=q.department_id AND ud.program_id IS NOT NULL;
UPDATE education_verifications ev SET program_id=ud.program_id
FROM university_departments ud
WHERE ev.program_id IS NULL AND ev.university_department_id=ud.id AND ud.program_id IS NOT NULL;
UPDATE evaluations value SET program_id=ud.program_id FROM university_departments ud
WHERE value.program_id IS NULL AND value.university_department_id=ud.id AND ud.program_id IS NOT NULL;
UPDATE polls value SET program_id=ud.program_id FROM university_departments ud
WHERE value.program_id IS NULL AND value.university_department_id=ud.id AND ud.program_id IS NOT NULL;
UPDATE context_metrics value SET program_id=ud.program_id FROM university_departments ud
WHERE value.program_id IS NULL AND value.university_department_id=ud.id AND ud.program_id IS NOT NULL;
UPDATE structured_experiences value SET program_id=ud.program_id FROM university_departments ud
WHERE value.program_id IS NULL AND value.university_department_id=ud.id AND ud.program_id IS NOT NULL;
UPDATE graduate_outcomes value SET program_id=ud.program_id FROM university_departments ud
WHERE value.program_id IS NULL AND value.university_department_id=ud.id AND ud.program_id IS NOT NULL;

UPDATE follows f SET target_id=ud.program_id FROM university_departments ud
WHERE f.target_type='PROGRAM' AND f.target_id=ud.id AND ud.program_id IS NOT NULL;
UPDATE saved_items s SET target_id=ud.program_id FROM university_departments ud
WHERE s.target_type='PROGRAM' AND s.target_id=ud.id AND ud.program_id IS NOT NULL;
UPDATE notifications n SET target_id=ud.program_id FROM university_departments ud
WHERE n.target_type='PROGRAM' AND n.target_id=ud.id AND ud.program_id IS NOT NULL;

CREATE INDEX idx_user_profiles_program ON user_profiles(program_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_questions_program ON questions(program_id,created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_education_verifications_program ON education_verifications(program_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_evaluations_normalized_program ON evaluations(program_id,created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_polls_normalized_program ON polls(program_id,created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_context_metrics_normalized_program ON context_metrics(program_id,metric_key) WHERE deleted_at IS NULL;
CREATE INDEX idx_structured_experiences_normalized_program ON structured_experiences(program_id,created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_graduate_outcomes_normalized_program ON graduate_outcomes(program_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_context_metrics_normalized_program ON context_metrics(user_id,university_id,program_id,metric_key) WHERE program_id IS NOT NULL;
CREATE UNIQUE INDEX uq_graduate_outcomes_normalized_program ON graduate_outcomes(user_id,program_id) WHERE program_id IS NOT NULL;
CREATE UNIQUE INDEX uq_evaluations_normalized_program ON evaluations(author_id,university_id,program_id) WHERE program_id IS NOT NULL;

-- V30 used a local variable named program_id. Once questions.program_id exists,
-- PostgreSQL cannot distinguish that variable from the NEW row field. Recreate
-- the trigger with explicit normalized and legacy identities.
CREATE OR REPLACE FUNCTION route_new_question() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
 normalized_program_id uuid := NEW.program_id;
 legacy_university_department_id uuid;
BEGIN
 IF normalized_program_id IS NULL THEN
  SELECT ud.program_id,ud.id
  INTO normalized_program_id,legacy_university_department_id
  FROM university_departments ud
  WHERE ud.university_id=NEW.university_id
    AND ud.department_id=NEW.department_id
    AND ud.deleted_at IS NULL
  ORDER BY ud.created_at
  LIMIT 1;
 ELSE
  SELECT ud.id
  INTO legacy_university_department_id
  FROM university_departments ud
  WHERE ud.program_id=normalized_program_id
    AND ud.deleted_at IS NULL
  ORDER BY ud.created_at
  LIMIT 1;
 END IF;

 INSERT INTO domain_outbox(id,event_type,aggregate_type,aggregate_id,payload)
 VALUES(md5('question-published:'||NEW.id)::uuid,'QUESTION_PUBLISHED','QUESTION',NEW.id,
        jsonb_build_object('questionId',NEW.id,'scope',NEW.scope,'universityId',NEW.university_id,
                           'programId',normalized_program_id))
 ON CONFLICT DO NOTHING;

 INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id)
 SELECT md5('routed-question:'||NEW.id||':'||candidate.user_id)::uuid,candidate.user_id,
        'QUESTION_ROUTED','Deneyimine uygun yeni soru',
        'Üniversite veya program deneyimine uygun yeni bir soru yayınlandı.','QUESTION',NEW.id
 FROM (
  SELECT DISTINCT f.user_id
  FROM follows f
  WHERE f.deleted_at IS NULL
    AND ((f.target_type='UNIVERSITY' AND f.target_id=NEW.university_id)
      OR (f.target_type='PROGRAM' AND f.target_id=normalized_program_id))
  UNION
  SELECT ev.user_id
  FROM education_verifications ev
  WHERE ev.verified_at IS NOT NULL
    AND ev.deleted_at IS NULL
    AND (ev.program_id=normalized_program_id
      OR (ev.program_id IS NULL AND ev.university_department_id=legacy_university_department_id))
 ) candidate
 LEFT JOIN notification_preferences np ON np.user_id=candidate.user_id
 WHERE candidate.user_id<>NEW.author_id
   AND coalesce(np.in_app_enabled,true)
   AND coalesce(np.question_routing_enabled,true)
 ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;
