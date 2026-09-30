-- Complete the Tanıdık tenure trio without changing existing awards or selections.
UPDATE achievement_definitions SET display_order=display_order+1 WHERE display_order>=18;
INSERT INTO achievement_definitions(achievement_key,title,description,icon,event_type,required_count,display_order)
VALUES('TENURE_5','5 Yıllık Tanıdık','Tanıdık onayının üzerinden 5 yıl geçsin ve toplam 500 puan kazandıran etkinliğe ulaş.','🌲🌲',NULL,NULL,18);

CREATE OR REPLACE FUNCTION award_point_achievements() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE approved_at timestamptz; activity_count bigint; seasonal_key text; seasonal_title text;
 period_months integer[]; event_year integer; event_month integer;
BEGIN
 IF NEW.points<=0 THEN RETURN NEW; END IF;
 PERFORM 1 FROM users WHERE id=NEW.user_id FOR UPDATE;
 SELECT count(*) INTO activity_count FROM point_events WHERE user_id=NEW.user_id AND points>0;
 SELECT min(coalesce(reviewed_at,created_at)) INTO approved_at FROM admin_applications WHERE applicant_id=NEW.user_id AND status='APPROVED' AND deleted_at IS NULL;
 IF approved_at<=CURRENT_TIMESTAMP-interval '1 year' AND activity_count>=50 THEN
  INSERT INTO user_achievements(id,user_id,achievement_key,title) VALUES(md5('tenure-1:'||NEW.user_id)::uuid,NEW.user_id,'TENURE_1','1 Yıllık Tanıdık') ON CONFLICT DO NOTHING;
 END IF;
 IF approved_at<=CURRENT_TIMESTAMP-interval '3 years' AND activity_count>=200 THEN
  INSERT INTO user_achievements(id,user_id,achievement_key,title) VALUES(md5('tenure-3:'||NEW.user_id)::uuid,NEW.user_id,'TENURE_3','3 Yıllık Tanıdık') ON CONFLICT DO NOTHING;
 END IF;
 IF approved_at<=CURRENT_TIMESTAMP-interval '5 years' AND activity_count>=500 THEN
  INSERT INTO user_achievements(id,user_id,achievement_key,title) VALUES(md5('tenure-5:'||NEW.user_id)::uuid,NEW.user_id,'TENURE_5','5 Yıllık Tanıdık') ON CONFLICT DO NOTHING;
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

-- Users who already satisfy both conditions receive the new badge immediately.
WITH approved AS (
 SELECT applicant_id AS user_id,min(coalesce(reviewed_at,created_at)) AS approved_at
 FROM admin_applications WHERE status='APPROVED' AND deleted_at IS NULL GROUP BY applicant_id
), activity AS (
 SELECT user_id,count(*) AS activity_count FROM point_events WHERE points>0 GROUP BY user_id
)
INSERT INTO user_achievements(id,user_id,achievement_key,title)
SELECT md5('tenure-5:'||approved.user_id)::uuid,approved.user_id,'TENURE_5','5 Yıllık Tanıdık'
FROM approved JOIN activity USING(user_id)
WHERE approved.approved_at<=CURRENT_TIMESTAMP-interval '5 years' AND activity.activity_count>=500
ON CONFLICT DO NOTHING;
