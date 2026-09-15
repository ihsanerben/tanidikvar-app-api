CREATE FUNCTION notify_context_followers() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE university uuid; program uuid; author uuid; kind text; heading text; message text;
BEGIN
 IF TG_TABLE_NAME='polls' THEN university=NEW.university_id;program=NEW.university_department_id;author=NEW.author_id;kind='NEW_POLL';heading='Yeni anket';message='Takip ettiğin toplulukta yeni bir anket açıldı.';
 ELSIF TG_TABLE_NAME='evaluations' THEN university=NEW.university_id;program=NEW.university_department_id;author=NEW.author_id;kind='NEW_EVALUATION';heading='Yeni değerlendirme';message='Takip ettiğin toplulukta yeni bir değerlendirme paylaşıldı.';
 ELSE university=NEW.university_id;program=NEW.university_department_id;author=NEW.author_id;kind='NEW_EXPERIENCE';heading='Yeni öğrenci deneyimi';message='Takip ettiğin toplulukta yeni bir deneyim paylaşıldı.'; END IF;
 INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id)
 SELECT md5(TG_TABLE_NAME||':'||NEW.id||':'||f.user_id)::uuid,f.user_id,kind,heading,message,CASE WHEN program IS NULL THEN 'UNIVERSITY' ELSE 'PROGRAM' END,coalesce(program,university)
 FROM follows f LEFT JOIN notification_preferences np ON np.user_id=f.user_id
 WHERE f.deleted_at IS NULL AND f.user_id<>author AND coalesce(np.in_app_enabled,true) AND ((f.target_type='UNIVERSITY' AND f.target_id=university) OR (f.target_type='PROGRAM' AND f.target_id=program)) ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;
CREATE TRIGGER polls_activity_notification AFTER INSERT ON polls FOR EACH ROW EXECUTE FUNCTION notify_context_followers();
CREATE TRIGGER evaluations_activity_notification AFTER INSERT ON evaluations FOR EACH ROW EXECUTE FUNCTION notify_context_followers();
CREATE TRIGGER experiences_activity_notification AFTER INSERT ON structured_experiences FOR EACH ROW EXECUTE FUNCTION notify_context_followers();

CREATE FUNCTION notify_answer_discussion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id)
 SELECT md5('answer-comment:'||NEW.id||':'||f.user_id)::uuid,f.user_id,'ANSWER_DISCUSSION','Takip ettiğin yanıtta yeni yorum','Bir yanıta yeni alt yorum eklendi.','ANSWER',NEW.answer_id
 FROM follows f JOIN answers a ON a.question_id=f.target_id LEFT JOIN notification_preferences np ON np.user_id=f.user_id
 WHERE f.target_type='QUESTION' AND f.deleted_at IS NULL AND a.id=NEW.answer_id AND f.user_id<>NEW.author_id AND coalesce(np.in_app_enabled,true) ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;
CREATE TRIGGER answer_comments_notification AFTER INSERT ON answer_comments FOR EACH ROW EXECUTE FUNCTION notify_answer_discussion();

CREATE FUNCTION notify_achievement_awarded() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id) VALUES(md5('achievement-note:'||NEW.id)::uuid,NEW.user_id,'ACHIEVEMENT','Yeni rozet kazandın',NEW.title,NULL,NULL) ON CONFLICT DO NOTHING;RETURN NEW;END $$;
CREATE TRIGGER achievement_notification AFTER INSERT ON user_achievements FOR EACH ROW EXECUTE FUNCTION notify_achievement_awarded();

CREATE FUNCTION notify_title_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE before_points bigint;after_points bigint;before_title text;after_title text;
BEGIN
 SELECT coalesce(sum(points),0) INTO after_points FROM point_events WHERE user_id=NEW.user_id;
 before_points=after_points-NEW.points;
 before_title=CASE WHEN before_points>=10000 THEN 'Efsane Tanıdık' WHEN before_points>=5000 THEN 'Usta Tanıdık' WHEN before_points>=2500 THEN 'Uzman Tanıdık' WHEN before_points>=1000 THEN 'Kıdemli Tanıdık' WHEN before_points>=500 THEN 'Deneyimli Tanıdık' WHEN before_points>=100 THEN 'Aktif Tanıdık' ELSE 'Yeni Tanıdık' END;
 after_title=CASE WHEN after_points>=10000 THEN 'Efsane Tanıdık' WHEN after_points>=5000 THEN 'Usta Tanıdık' WHEN after_points>=2500 THEN 'Uzman Tanıdık' WHEN after_points>=1000 THEN 'Kıdemli Tanıdık' WHEN after_points>=500 THEN 'Deneyimli Tanıdık' WHEN after_points>=100 THEN 'Aktif Tanıdık' ELSE 'Yeni Tanıdık' END;
 IF before_title<>after_title THEN INSERT INTO notifications(id,user_id,notification_type,title,body) VALUES(md5('title-note:'||NEW.id)::uuid,NEW.user_id,'TITLE_UPGRADED','Yeni title kazandın',after_title) ON CONFLICT DO NOTHING;END IF;RETURN NEW;
END $$;
CREATE TRIGGER point_title_notification AFTER INSERT ON point_events FOR EACH ROW EXECUTE FUNCTION notify_title_change();
