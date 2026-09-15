-- Existing visible contributions receive the same policy-v1 credit as new ones.
INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version,created_at)
 SELECT md5('question:'||q.id)::uuid,q.author_id,'QUESTION_CREATED',5,'QUESTION',q.id,1,q.created_at FROM questions q WHERE q.deleted_at IS NULL ON CONFLICT DO NOTHING;
INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version,created_at)
 SELECT md5('answer:'||a.id)::uuid,a.author_id,'ANSWER_CREATED',10,'ANSWER',a.id,1,a.created_at FROM answers a WHERE a.deleted_at IS NULL AND a.moderated_at IS NULL ON CONFLICT DO NOTHING;
INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version,created_at)
 SELECT md5('evaluation:'||e.id)::uuid,e.author_id,'EVALUATION_CREATED',8,'EVALUATION',e.id,1,e.created_at FROM evaluations e WHERE e.deleted_at IS NULL ON CONFLICT DO NOTHING;

CREATE FUNCTION reward_question_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES(md5('question:'||NEW.id)::uuid,NEW.author_id,'QUESTION_CREATED',5,'QUESTION',NEW.id,1) ON CONFLICT DO NOTHING;RETURN NEW;END $$;
CREATE FUNCTION reward_answer_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES(md5('answer:'||NEW.id)::uuid,NEW.author_id,'ANSWER_CREATED',10,'ANSWER',NEW.id,1) ON CONFLICT DO NOTHING;RETURN NEW;END $$;
CREATE FUNCTION reward_evaluation_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES(md5('evaluation:'||NEW.id)::uuid,NEW.author_id,'EVALUATION_CREATED',8,'EVALUATION',NEW.id,1) ON CONFLICT DO NOTHING;RETURN NEW;END $$;
CREATE FUNCTION reward_poll_vote_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES(md5('poll-vote:'||NEW.id)::uuid,NEW.voter_id,'POLL_PARTICIPATION',1,'POLL',NEW.poll_id,1) ON CONFLICT DO NOTHING;RETURN NEW;END $$;
CREATE FUNCTION reward_answer_like_change() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE owner uuid;BEGIN
 SELECT author_id INTO owner FROM answers WHERE id=NEW.answer_id;
 IF NEW.deleted_at IS NULL AND (TG_OP='INSERT' OR OLD.deleted_at IS NOT NULL) THEN
  INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES(md5('helpful:'||NEW.answer_id||':'||NEW.user_id)::uuid,owner,'HELPFUL_RECEIVED',3,'ANSWER_LIKE',md5('helpful-source:'||NEW.answer_id||':'||NEW.user_id)::uuid,1) ON CONFLICT DO NOTHING;
  INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id) VALUES(md5('helpful-note:'||NEW.answer_id||':'||NEW.user_id)::uuid,owner,'HELPFUL_VOTE','Yanıtın faydalı bulundu','Bir topluluk üyesi yanıtını faydalı buldu.','ANSWER',NEW.answer_id) ON CONFLICT DO NOTHING;
 ELSIF TG_OP='UPDATE' AND NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL THEN
  INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version) VALUES(md5('helpful-remove:'||NEW.answer_id||':'||NEW.user_id)::uuid,owner,'HELPFUL_REMOVED',-3,'ANSWER_LIKE',md5('helpful-source:'||NEW.answer_id||':'||NEW.user_id)::uuid,1) ON CONFLICT DO NOTHING;
 END IF;RETURN NEW;END $$;
CREATE TRIGGER questions_reward AFTER INSERT ON questions FOR EACH ROW EXECUTE FUNCTION reward_question_insert();
CREATE TRIGGER answers_reward AFTER INSERT ON answers FOR EACH ROW EXECUTE FUNCTION reward_answer_insert();
CREATE TRIGGER evaluations_reward AFTER INSERT ON evaluations FOR EACH ROW EXECUTE FUNCTION reward_evaluation_insert();
CREATE TRIGGER poll_votes_reward AFTER INSERT ON poll_votes FOR EACH ROW EXECUTE FUNCTION reward_poll_vote_insert();
CREATE TRIGGER answer_likes_reward AFTER INSERT OR UPDATE OF deleted_at ON answer_likes FOR EACH ROW EXECUTE FUNCTION reward_answer_like_change();
