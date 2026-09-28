-- Existing awards and showcase selections remain intact; new awards use these rules.
UPDATE achievement_definitions d SET required_count=v.required_count,description=v.description
FROM (VALUES
 ('FIRST_QUESTION',5,'Puan kazandıran 5 soru paylaş.'),
 ('QUESTIONS_10',25,'Puan kazandıran 25 soru paylaş.'),
 ('FIRST_ANSWER',10,'Puan kazandıran 10 yorum paylaş.'),
 ('ANSWERS_10',50,'Puan kazandıran 50 yorum paylaş.'),
 ('ANSWERS_50',200,'Puan kazandıran 200 yorum paylaş.'),
 ('FIRST_HELPFUL',10,'Yorumlarınla puan kazandıran 10 faydalı oy kazan.'),
 ('HELP_10',50,'Yorumlarınla puan kazandıran 50 faydalı oy kazan.'),
 ('FIRST_POLL',3,'Puan kazandıran 3 anket oluştur.'),
 ('VOTES_10',30,'Puan kazandıran 30 farklı ankete katıl.'),
 ('FIRST_EXPERIENCE',3,'Puan kazandıran 3 yapılandırılmış deneyim paylaş.'),
 ('EXPERIENCES_5',20,'Puan kazandıran 20 yapılandırılmış deneyim paylaş.'),
 ('FIRST_METRIC',3,'Puan kazandıran 3 farklı ölçüm katkısı yap.')
) AS v(key,required_count,description) WHERE d.achievement_key=v.key;
UPDATE achievement_definitions SET description=description||' Ayrıca en az 5 farklı günde puan kazandıran etkinliğin olsun.' WHERE event_type IS NOT NULL;
UPDATE achievement_definitions SET description='Tanıdık onayının üzerinden 1 yıl geçsin ve toplam 50 puan kazandıran etkinliğe ulaş.' WHERE achievement_key='TENURE_1';
UPDATE achievement_definitions SET description='Tanıdık onayının üzerinden 3 yıl geçsin ve toplam 200 puan kazandıran etkinliğe ulaş.' WHERE achievement_key='TENURE_3';
UPDATE achievement_definitions SET description=CASE achievement_key
 WHEN 'PREFERENCE_GUIDE' THEN 'Aynı yılın Haziran–Ağustos döneminde'
 WHEN 'NEW_STUDENT_GUIDE' THEN 'Aynı yılın Eylül–Ekim döneminde'
 WHEN 'FINAL_GUIDE' THEN 'Aynı yılın Ocak ve Mayıs aylarında'
 WHEN 'ERASMUS_GUIDE' THEN 'Aynı yılın Şubat–Mart döneminde'
 WHEN 'INTERNSHIP_GUIDE' THEN 'Aynı yılın Nisan ayında' END
 ||' en az 5 farklı güne yayılan 25 puan kazandıran etkinliğe ulaş.'
 WHERE achievement_key IN ('PREFERENCE_GUIDE','NEW_STUDENT_GUIDE','FINAL_GUIDE','ERASMUS_GUIDE','INTERNSHIP_GUIDE');

CREATE OR REPLACE FUNCTION award_task_achievements() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.points<=0 THEN RETURN NEW; END IF;
 PERFORM 1 FROM users WHERE id=NEW.user_id FOR UPDATE;
 IF (SELECT count(DISTINCT (created_at AT TIME ZONE 'Europe/Istanbul')::date) FROM point_events WHERE user_id=NEW.user_id AND points>0)<5 THEN RETURN NEW; END IF;
 -- Recheck all tasks when another active day is reached, even if its event type differs.
 INSERT INTO user_achievements(id,user_id,achievement_key,title)
 SELECT md5('task:'||NEW.user_id||':'||d.achievement_key)::uuid,NEW.user_id,d.achievement_key,d.title
 FROM achievement_definitions d WHERE d.event_type IS NOT NULL AND
 (SELECT count(*) FROM point_events e WHERE e.user_id=NEW.user_id AND e.event_type=d.event_type AND e.points>0)>=d.required_count
 ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION award_point_achievements() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE approved_at timestamptz; activity_count bigint; seasonal_key text; seasonal_title text;
 period_months integer[]; event_year integer; event_month integer;
BEGIN
 IF NEW.points<=0 THEN RETURN NEW; END IF;
 PERFORM 1 FROM users WHERE id=NEW.user_id FOR UPDATE;
 -- Helpful-vote awards now use the task catalog too, including its active-day gate.
 SELECT count(*) INTO activity_count FROM point_events WHERE user_id=NEW.user_id AND points>0;
 SELECT min(coalesce(reviewed_at,created_at)) INTO approved_at FROM admin_applications WHERE applicant_id=NEW.user_id AND status='APPROVED' AND deleted_at IS NULL;
 IF approved_at<=CURRENT_TIMESTAMP-interval '1 year' AND activity_count>=50 THEN
  INSERT INTO user_achievements(id,user_id,achievement_key,title) VALUES(md5('tenure-1:'||NEW.user_id)::uuid,NEW.user_id,'TENURE_1','1 Yıllık Tanıdık') ON CONFLICT DO NOTHING;
 END IF;
 IF approved_at<=CURRENT_TIMESTAMP-interval '3 years' AND activity_count>=200 THEN
  INSERT INTO user_achievements(id,user_id,achievement_key,title) VALUES(md5('tenure-3:'||NEW.user_id)::uuid,NEW.user_id,'TENURE_3','3 Yıllık Tanıdık') ON CONFLICT DO NOTHING;
 END IF;
 event_year=extract(year from NEW.created_at AT TIME ZONE 'Europe/Istanbul')::integer;
 event_month=extract(month from NEW.created_at AT TIME ZONE 'Europe/Istanbul')::integer;
 IF event_month BETWEEN 6 AND 8 THEN seasonal_key='PREFERENCE_GUIDE';period_months=ARRAY[6,7,8];
 ELSIF event_month BETWEEN 9 AND 10 THEN seasonal_key='NEW_STUDENT_GUIDE';period_months=ARRAY[9,10];
 ELSIF event_month IN (1,5) THEN seasonal_key='FINAL_GUIDE';period_months=ARRAY[1,5];
 ELSIF event_month BETWEEN 2 AND 3 THEN seasonal_key='ERASMUS_GUIDE';period_months=ARRAY[2,3];
 ELSIF event_month=4 THEN seasonal_key='INTERNSHIP_GUIDE';period_months=ARRAY[4]; END IF;
 IF seasonal_key IS NOT NULL AND EXISTS(
  SELECT 1 FROM point_events WHERE user_id=NEW.user_id AND points>0
   AND extract(year from created_at AT TIME ZONE 'Europe/Istanbul')=event_year
   AND extract(month from created_at AT TIME ZONE 'Europe/Istanbul')::integer=ANY(period_months)
  HAVING count(*)>=25 AND count(DISTINCT (created_at AT TIME ZONE 'Europe/Istanbul')::date)>=5
 ) THEN
  SELECT title INTO seasonal_title FROM achievement_definitions WHERE achievement_key=seasonal_key;
  INSERT INTO user_achievements(id,user_id,achievement_key,title,period_year)
  VALUES(md5('season:'||NEW.user_id||':'||seasonal_key||':'||event_year)::uuid,NEW.user_id,seasonal_key,event_year||' '||seasonal_title,event_year) ON CONFLICT DO NOTHING;
 END IF;
 RETURN NEW;
END $$;
