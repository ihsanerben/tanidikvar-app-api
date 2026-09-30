-- Policy v2 applies to existing and future visible contributions.
ALTER TABLE point_events DROP CONSTRAINT point_events_points_check;
ALTER TABLE point_events ADD CONSTRAINT point_events_points_check CHECK(points BETWEEN -10000 AND 10000);
UPDATE point_events SET points=CASE event_type
  WHEN 'QUESTION_CREATED' THEN 10
  WHEN 'ANSWER_CREATED' THEN 5
  WHEN 'EXPERIENCE_CREATED' THEN 3
  WHEN 'POLL_CREATED' THEN 3
  WHEN 'POLL_PARTICIPATION' THEN 1
  WHEN 'EVALUATION_CREATED' THEN 1
  WHEN 'METRIC_CONTRIBUTION' THEN 1
  WHEN 'HELPFUL_RECEIVED' THEN 0
  WHEN 'HELPFUL_REMOVED' THEN 0
  WHEN 'BEST_ANSWER' THEN 0
  ELSE points END,
  policy_version=2
WHERE event_type IN ('QUESTION_CREATED','ANSWER_CREATED','EXPERIENCE_CREATED','POLL_CREATED','POLL_PARTICIPATION','EVALUATION_CREATED','METRIC_CONTRIBUTION','HELPFUL_RECEIVED','HELPFUL_REMOVED','BEST_ANSWER');

CREATE OR REPLACE VIEW user_point_totals AS
 SELECT user_id,coalesce(sum(points),0)::bigint total_points,count(*) FILTER(WHERE points<>0)::bigint event_count,
 max(created_at) FILTER(WHERE points<>0) last_event_at FROM point_events GROUP BY user_id;

CREATE OR REPLACE FUNCTION reward_question_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version)
 VALUES(md5('question:'||NEW.id)::uuid,NEW.author_id,'QUESTION_CREATED',10,'QUESTION',NEW.id,2) ON CONFLICT DO NOTHING;
 RETURN NEW;END $$;
INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version,created_at)
SELECT md5('answer:'||id)::uuid,author_id,'ANSWER_CREATED',5,'ANSWER',id,2,created_at
FROM answers WHERE deleted_at IS NULL AND moderated_at IS NULL ON CONFLICT DO NOTHING;
CREATE OR REPLACE FUNCTION reward_answer_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version)
 VALUES(md5('answer:'||NEW.id)::uuid,NEW.author_id,'ANSWER_CREATED',5,'ANSWER',NEW.id,2) ON CONFLICT DO NOTHING;
 RETURN NEW;END $$;
CREATE OR REPLACE FUNCTION reward_evaluation_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version)
 VALUES(md5('evaluation:'||NEW.id)::uuid,NEW.author_id,'EVALUATION_CREATED',1,'EVALUATION',NEW.id,2) ON CONFLICT DO NOTHING;
 RETURN NEW;END $$;
CREATE OR REPLACE FUNCTION reward_poll_vote_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version)
 VALUES(md5('poll-vote:'||NEW.id)::uuid,NEW.voter_id,'POLL_PARTICIPATION',1,'POLL',NEW.poll_id,2) ON CONFLICT DO NOTHING;
 RETURN NEW;END $$;
CREATE OR REPLACE FUNCTION reward_experience_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version)
 VALUES(md5('experience:'||NEW.id)::uuid,NEW.author_id,'EXPERIENCE_CREATED',3,'EXPERIENCE',NEW.id,2) ON CONFLICT DO NOTHING;
 IF NEW.template_type='NEW_STUDENT_GUIDE' THEN
  INSERT INTO user_achievements(id,user_id,achievement_key,title,period_year,scope_type,scope_id)
  VALUES(md5('new-student-guide:'||NEW.author_id||':'||extract(year from NEW.created_at)||':'||NEW.university_id)::uuid,NEW.author_id,'NEW_STUDENT_GUIDE','Yeni Kazananlar Rehberi',extract(year from NEW.created_at)::int,'UNIVERSITY',NEW.university_id) ON CONFLICT DO NOTHING;
 END IF;
 RETURN NEW;END $$;
CREATE OR REPLACE FUNCTION reward_metric_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version)
 VALUES(md5('metric:'||NEW.id)::uuid,NEW.user_id,'METRIC_CONTRIBUTION',1,'CONTEXT_METRIC',NEW.id,2) ON CONFLICT DO NOTHING;
 RETURN NEW;END $$;
CREATE OR REPLACE FUNCTION reward_poll_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version)
 VALUES(md5('poll:'||NEW.id)::uuid,NEW.author_id,'POLL_CREATED',3,'POLL',NEW.id,2) ON CONFLICT DO NOTHING;
 RETURN NEW;END $$;

-- An answer comment is a written contribution as well.
INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version,created_at)
SELECT md5('answer-comment:'||id)::uuid,author_id,'ANSWER_COMMENT_CREATED',5,'ANSWER_COMMENT',id,2,created_at
FROM answer_comments WHERE deleted_at IS NULL ON CONFLICT DO NOTHING;
CREATE FUNCTION reward_answer_comment_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO point_events(id,user_id,event_type,points,source_type,source_id,policy_version)
  VALUES(md5('answer-comment:'||NEW.id)::uuid,NEW.author_id,'ANSWER_COMMENT_CREATED',5,'ANSWER_COMMENT',NEW.id,2) ON CONFLICT DO NOTHING;
 RETURN NEW;END $$;
CREATE TRIGGER answer_comments_reward AFTER INSERT ON answer_comments FOR EACH ROW EXECUTE FUNCTION reward_answer_comment_insert();

DROP TRIGGER answer_likes_reward ON answer_likes;
DROP FUNCTION reward_answer_like_change();

-- Retire reward definitions tied to the removed helpful vote feature.
DELETE FROM achievement_definitions WHERE achievement_key IN ('FIRST_HELPFUL','HELP_10','HELP_100','HELP_500');
