CREATE OR REPLACE FUNCTION reward_answer_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE normalized text;
BEGIN
 normalized=lower(regexp_replace(trim(NEW.body),'\s+',' ','g'));
 IF length(normalized)<80 THEN
  INSERT INTO gamification_fraud_signals(id,user_id,signal_type,source_type,source_id,detail) VALUES(md5('short-answer:'||NEW.id)::uuid,NEW.author_id,'LOW_QUALITY_ANSWER','ANSWER',NEW.id,jsonb_build_object('length',length(normalized))) ON CONFLICT DO NOTHING;
  RETURN NEW;
 END IF;
 IF EXISTS(SELECT 1 FROM answers prior_answer WHERE prior_answer.author_id=NEW.author_id AND prior_answer.id<>NEW.id AND prior_answer.deleted_at IS NULL AND lower(regexp_replace(trim(prior_answer.body),'\s+',' ','g'))=normalized) THEN
  INSERT INTO gamification_fraud_signals(id,user_id,signal_type,source_type,source_id) VALUES(md5('duplicate-answer:'||NEW.id)::uuid,NEW.author_id,'DUPLICATE_CONTENT','ANSWER',NEW.id) ON CONFLICT DO NOTHING;
  RETURN NEW;
 END IF;
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES(md5('answer:'||NEW.id)::uuid,NEW.author_id,'ANSWER_CREATED',10,'ANSWER',NEW.id,1) ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;
