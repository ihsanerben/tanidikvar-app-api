-- Public/domain role terminology changes without deleting applications, answers or audit history.
ALTER TABLE users DROP CONSTRAINT users_authority_check;
ALTER TABLE users ADD CONSTRAINT users_authority_check CHECK (authority IN ('MEMBER','TANIDIK','MANAGER')) NOT VALID;
UPDATE users SET authority='TANIDIK',updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE authority='ADMIN';
ALTER TABLE users VALIDATE CONSTRAINT users_authority_check;

ALTER TABLE answers DROP CONSTRAINT chk_anonymous_tanidik;
ALTER TABLE answers DROP CONSTRAINT chk_answers_kind;
ALTER TABLE answers DISABLE TRIGGER answer_verification_guard;
UPDATE answers SET answer_kind='TANIDIK' WHERE answer_kind='ADMIN';
ALTER TABLE answers ENABLE TRIGGER answer_verification_guard;
ALTER TABLE answers ADD CONSTRAINT chk_answers_kind CHECK
 ((answer_kind='COMMUNITY' AND verification_application_id IS NULL) OR (answer_kind='TANIDIK' AND verification_application_id IS NOT NULL));
ALTER TABLE answers ADD CONSTRAINT chk_anonymous_tanidik CHECK(NOT anonymous OR answer_kind='TANIDIK');

CREATE OR REPLACE FUNCTION protect_answer_verification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND ROW(NEW.answer_kind,NEW.author_id,NEW.question_id,NEW.verification_application_id,NEW.published_at)
 IS DISTINCT FROM ROW(OLD.answer_kind,OLD.author_id,OLD.question_id,OLD.verification_application_id,OLD.published_at)
 THEN RAISE EXCEPTION 'Answer publication identity is immutable' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' AND NEW.answer_kind='TANIDIK' AND NOT EXISTS
 (SELECT 1 FROM admin_applications WHERE id=NEW.verification_application_id AND applicant_id=NEW.author_id AND status='APPROVED' AND deleted_at IS NULL)
 THEN RAISE EXCEPTION 'Approved Tanidik verification required' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION notify_question_followers() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id)
  SELECT md5('question-answer:'||NEW.id||':'||f.user_id)::uuid,f.user_id,'FOLLOWED_QUESTION_ANSWERED','Takip ettiğin soru yanıtlandı','Takip ettiğin soruya yeni bir deneyim eklendi.','QUESTION',NEW.question_id
  FROM follows f WHERE f.target_type='QUESTION' AND f.target_id=NEW.question_id AND f.deleted_at IS NULL AND f.user_id<>NEW.author_id ON CONFLICT DO NOTHING;
  INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id)
  SELECT md5('tanidik-answer:'||NEW.id||':'||f.user_id)::uuid,f.user_id,'FOLLOWED_TANIDIK_ANSWERED','Takip ettiğin Tanıdık yanıt verdi','Takip ettiğin Tanıdık yeni bir deneyim paylaştı.','ANSWER',NEW.id
  FROM follows f WHERE NEW.answer_kind='TANIDIK' AND f.target_type='TANIDIK' AND f.target_id=NEW.author_id AND f.deleted_at IS NULL AND f.user_id<>NEW.author_id ON CONFLICT DO NOTHING;
  RETURN NEW;
END $$;
