CREATE OR REPLACE FUNCTION reward_answer_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE normalized text;
BEGIN
 normalized=lower(regexp_replace(trim(NEW.body),'\s+',' ','g'));
 IF length(normalized)<80 THEN
  INSERT INTO gamification_fraud_signals(id,user_id,signal_type,source_type,source_id,detail) VALUES(md5('short-answer:'||NEW.id)::uuid,NEW.author_id,'LOW_QUALITY_ANSWER','ANSWER',NEW.id,jsonb_build_object('length',length(normalized))) ON CONFLICT DO NOTHING;
  RETURN NEW;
 END IF;
 IF EXISTS(SELECT 1 FROM answers old WHERE old.author_id=NEW.author_id AND old.id<>NEW.id AND old.deleted_at IS NULL AND lower(regexp_replace(trim(old.body),'\s+',' ','g'))=normalized) THEN
  INSERT INTO gamification_fraud_signals(id,user_id,signal_type,source_type,source_id) VALUES(md5('duplicate-answer:'||NEW.id)::uuid,NEW.author_id,'DUPLICATE_CONTENT','ANSWER',NEW.id) ON CONFLICT DO NOTHING;
  RETURN NEW;
 END IF;
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES(md5('answer:'||NEW.id)::uuid,NEW.author_id,'ANSWER_CREATED',10,'ANSWER',NEW.id,1) ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION reward_answer_like_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE owner uuid; reciprocal bigint; incoming bigint;
BEGIN
 SELECT author_id INTO owner FROM answers WHERE id=NEW.answer_id;
 IF owner=NEW.user_id THEN
  INSERT INTO gamification_fraud_signals(id,user_id,signal_type,source_type,source_id) VALUES(md5('self-like:'||NEW.answer_id||':'||NEW.user_id)::uuid,NEW.user_id,'SELF_VOTE','ANSWER_LIKE',NEW.answer_id) ON CONFLICT DO NOTHING;RETURN NEW;
 END IF;
 IF NEW.deleted_at IS NULL AND (TG_OP='INSERT' OR OLD.deleted_at IS NOT NULL) THEN
  SELECT count(*) INTO reciprocal FROM answer_likes l JOIN answers a ON a.id=l.answer_id WHERE l.user_id=owner AND a.author_id=NEW.user_id AND l.deleted_at IS NULL AND l.created_at>=CURRENT_TIMESTAMP-interval '30 days';
  SELECT count(*) INTO incoming FROM answer_likes l JOIN answers a ON a.id=l.answer_id WHERE l.user_id=NEW.user_id AND a.author_id=owner AND l.deleted_at IS NULL AND l.created_at>=CURRENT_TIMESTAMP-interval '30 days';
  IF reciprocal>=3 AND incoming>=3 THEN
   INSERT INTO gamification_fraud_signals(id,user_id,signal_type,source_type,source_id,detail) VALUES(md5('reciprocal:'||NEW.answer_id||':'||NEW.user_id)::uuid,owner,'RECIPROCAL_VOTE_CLUSTER','ANSWER_LIKE',NEW.answer_id,jsonb_build_object('reciprocal',reciprocal,'incoming',incoming)) ON CONFLICT DO NOTHING;RETURN NEW;
  END IF;
  INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES(md5('helpful:'||NEW.answer_id||':'||NEW.user_id)::uuid,owner,'HELPFUL_RECEIVED',3,'ANSWER_LIKE',md5('helpful-source:'||NEW.answer_id||':'||NEW.user_id)::uuid,1) ON CONFLICT DO NOTHING;
  INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id) VALUES(md5('helpful-note:'||NEW.answer_id||':'||NEW.user_id)::uuid,owner,'HELPFUL_VOTE','Yanıtın faydalı bulundu','Bir topluluk üyesi yanıtını faydalı buldu.','ANSWER',NEW.answer_id) ON CONFLICT DO NOTHING;
 ELSIF TG_OP='UPDATE' AND NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL THEN
  INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES(md5('helpful-remove:'||NEW.answer_id||':'||NEW.user_id)::uuid,owner,'HELPFUL_REMOVED',-3,'ANSWER_LIKE',md5('helpful-source:'||NEW.answer_id||':'||NEW.user_id)::uuid,1) ON CONFLICT DO NOTHING;
 END IF;RETURN NEW;
END $$;
