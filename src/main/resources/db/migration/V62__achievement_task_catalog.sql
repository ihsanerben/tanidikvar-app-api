CREATE TABLE achievement_definitions (
 achievement_key varchar(80) PRIMARY KEY,title varchar(120) NOT NULL,
 description text NOT NULL,icon varchar(12) NOT NULL,event_type varchar(80),required_count integer,
 display_order integer NOT NULL,CHECK ((event_type IS NULL) = (required_count IS NULL)),CHECK(required_count>0)
);
INSERT INTO achievement_definitions(achievement_key,title,description,icon,event_type,required_count,display_order) VALUES
('FIRST_QUESTION','İlk Merak','Puan kazandıran ilk sorunu paylaş.','?','QUESTION_CREATED',1,0),
('QUESTIONS_10','Meraklı Zihin','Puan kazandıran 10 soru paylaş.','?','QUESTION_CREATED',10,1),
('FIRST_ANSWER','İlk Destek','Puan kazandıran ilk yorumunu paylaş.','✦','ANSWER_CREATED',1,2),
('ANSWERS_10','Yol Arkadaşı','Puan kazandıran 10 yorum paylaş.','✦','ANSWER_CREATED',10,3),
('ANSWERS_50','Topluluk Rehberi','Puan kazandıran 50 yorum paylaş.','★','ANSWER_CREATED',50,4),
('FIRST_HELPFUL','Birine Dokundun','İlk faydalı oyunu kazan.','♥','HELPFUL_RECEIVED',1,5),
('HELP_10','Destek Eli','Yorumlarınla 10 faydalı oy kazan.','♥','HELPFUL_RECEIVED',10,6),
('HELP_100','100 Kişiye Yardımcı Oldu','Yorumlarınla 100 faydalı oy kazan.','♥','HELPFUL_RECEIVED',100,7),
('HELP_500','500 Kişiye Yardımcı Oldu','Yorumlarınla 500 faydalı oy kazan.','♥','HELPFUL_RECEIVED',500,8),
('FIRST_EVALUATION','Kampüs Gözlemcisi','Üniversiten için ilk değerlendirmeyi paylaş.','★','EVALUATION_CREATED',1,9),
('FIRST_POLL','Sözü Topluluğa Ver','İlk anketini oluştur.','▥','POLL_CREATED',1,10),
('VOTES_10','Katılımcı','10 farklı ankete katıl.','✓','POLL_PARTICIPATION',10,11),
('FIRST_EXPERIENCE','Deneyim Paylaşan','İlk yapılandırılmış deneyimini paylaş.','✎','EXPERIENCE_CREATED',1,12),
('EXPERIENCES_5','Deneyim Elçisi','5 yapılandırılmış deneyim paylaş.','✎','EXPERIENCE_CREATED',5,13),
('FIRST_METRIC','Gerçek Hayattan','İlk gerçek hayat ölçümünü paylaş.','▥','METRIC_CONTRIBUTION',1,14),
('METRICS_10','Veri Gönüllüsü','10 ölçüm katkısıyla topluluğa destek ol.','▥','METRIC_CONTRIBUTION',10,15),
('TENURE_1','1 Yıllık Tanıdık','Tanıdık onayının birinci yılından sonra puan kazandıran bir katkı yap.','◷',NULL,NULL,16),
('TENURE_3','3 Yıllık Tanıdık','Tanıdık onayının üçüncü yılından sonra puan kazandıran bir katkı yap.','◷',NULL,NULL,17),
('PREFERENCE_GUIDE','Tercih Rehberi','Haziran–Ağustos döneminde puan kazandıran katkı yap.','☀',NULL,NULL,18),
('NEW_STUDENT_GUIDE','Yeni Kazananlar Rehberi','Eylül–Ekim döneminde puan kazandıran katkı yap.','✦',NULL,NULL,19),
('FINAL_GUIDE','Final Rehberi','Ocak veya Mayıs ayında puan kazandıran katkı yap.','★',NULL,NULL,20),
('ERASMUS_GUIDE','Erasmus Rehberi','Şubat–Mart döneminde puan kazandıran katkı yap.','✈',NULL,NULL,21),
('INTERNSHIP_GUIDE','Staj Rehberi','Nisan ayında puan kazandıran katkı yap.','✓',NULL,NULL,22);
CREATE FUNCTION award_task_achievements() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.points<=0 THEN RETURN NEW; END IF;
 PERFORM 1 FROM users WHERE id=NEW.user_id FOR UPDATE;
 INSERT INTO user_achievements(id,user_id,achievement_key,title)
 SELECT md5('task:'||NEW.user_id||':'||d.achievement_key)::uuid,NEW.user_id,d.achievement_key,d.title
 FROM achievement_definitions d WHERE d.event_type=NEW.event_type AND
 (SELECT count(*) FROM point_events e WHERE e.user_id=NEW.user_id AND e.event_type=d.event_type AND e.points>0)>=d.required_count
 ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;
CREATE TRIGGER point_events_task_achievements AFTER INSERT ON point_events FOR EACH ROW EXECUTE FUNCTION award_task_achievements();
-- Existing contributions unlock the same tasks; stable keys make this idempotent.
INSERT INTO user_achievements(id,user_id,achievement_key,title)
 SELECT md5('task:'||e.user_id||':'||d.achievement_key)::uuid,e.user_id,d.achievement_key,d.title
 FROM achievement_definitions d JOIN point_events e ON e.event_type=d.event_type AND e.points>0
 GROUP BY e.user_id,d.achievement_key,d.title,d.required_count HAVING count(*)>=d.required_count
 ON CONFLICT DO NOTHING;
