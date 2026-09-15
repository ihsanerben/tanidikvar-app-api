ALTER TABLE user_achievements ADD COLUMN featured boolean NOT NULL DEFAULT false;
CREATE INDEX idx_user_achievements_featured ON user_achievements(user_id,featured,awarded_at DESC);

CREATE TABLE gamification_fraud_signals (
 id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 signal_type varchar(40) NOT NULL,source_type varchar(30) NOT NULL,source_id uuid NOT NULL,
 detail jsonb NOT NULL DEFAULT '{}'::jsonb,reviewed_at timestamptz,created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(user_id,signal_type,source_type,source_id)
);
CREATE INDEX idx_gamification_fraud_review ON gamification_fraud_signals(created_at DESC) WHERE reviewed_at IS NULL;
CREATE TRIGGER gamification_fraud_no_delete BEFORE DELETE OR TRUNCATE ON gamification_fraud_signals FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();

CREATE FUNCTION award_point_achievements() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE helpful bigint; approved_at timestamptz; seasonal_key text; seasonal_title text;
BEGIN
 SELECT count(*) INTO helpful FROM point_events WHERE user_id=NEW.user_id AND event_type='HELPFUL_RECEIVED';
 IF helpful>=100 THEN INSERT INTO user_achievements(id,user_id,achievement_key,title) VALUES(md5('help-100:'||NEW.user_id)::uuid,NEW.user_id,'HELP_100','100 Kişiye Yardımcı Oldu') ON CONFLICT DO NOTHING; END IF;
 IF helpful>=500 THEN INSERT INTO user_achievements(id,user_id,achievement_key,title) VALUES(md5('help-500:'||NEW.user_id)::uuid,NEW.user_id,'HELP_500','500 Kişiye Yardımcı Oldu') ON CONFLICT DO NOTHING; END IF;
 SELECT min(created_at) INTO approved_at FROM admin_applications WHERE applicant_id=NEW.user_id AND status='APPROVED' AND deleted_at IS NULL;
 IF approved_at<=CURRENT_TIMESTAMP-interval '1 year' THEN INSERT INTO user_achievements(id,user_id,achievement_key,title) VALUES(md5('tenure-1:'||NEW.user_id)::uuid,NEW.user_id,'TENURE_1','1 Yıllık Tanıdık') ON CONFLICT DO NOTHING; END IF;
 IF approved_at<=CURRENT_TIMESTAMP-interval '3 years' THEN INSERT INTO user_achievements(id,user_id,achievement_key,title) VALUES(md5('tenure-3:'||NEW.user_id)::uuid,NEW.user_id,'TENURE_3','3 Yıllık Tanıdık') ON CONFLICT DO NOTHING; END IF;
 IF extract(month from NEW.created_at) BETWEEN 6 AND 8 THEN seasonal_key='PREFERENCE_GUIDE';seasonal_title='Tercih Rehberi';
 ELSIF extract(month from NEW.created_at) BETWEEN 9 AND 10 THEN seasonal_key='NEW_STUDENT_GUIDE';seasonal_title='Yeni Kazananlar Rehberi';
 ELSIF extract(month from NEW.created_at) IN (1,5) THEN seasonal_key='FINAL_GUIDE';seasonal_title='Final Rehberi';
 ELSIF extract(month from NEW.created_at) BETWEEN 2 AND 3 THEN seasonal_key='ERASMUS_GUIDE';seasonal_title='Erasmus Rehberi';
 ELSIF extract(month from NEW.created_at)=4 THEN seasonal_key='INTERNSHIP_GUIDE';seasonal_title='Staj Rehberi'; END IF;
 IF seasonal_key IS NOT NULL AND NEW.points>0 THEN INSERT INTO user_achievements(id,user_id,achievement_key,title,period_year) VALUES(md5('season:'||NEW.user_id||':'||seasonal_key||':'||extract(year from NEW.created_at))::uuid,NEW.user_id,seasonal_key,extract(year from NEW.created_at)::int||' '||seasonal_title,extract(year from NEW.created_at)::int) ON CONFLICT DO NOTHING; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER point_events_achievements AFTER INSERT ON point_events FOR EACH ROW EXECUTE FUNCTION award_point_achievements();
