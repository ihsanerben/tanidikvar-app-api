CREATE FUNCTION notify_question_followers() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id)
  SELECT md5('question-answer:'||NEW.id||':'||f.user_id)::uuid,
         f.user_id,
         'FOLLOWED_QUESTION_ANSWERED',
         'Takip ettiğin soru yanıtlandı',
         'Takip ettiğin soruya yeni bir deneyim eklendi.',
         'QUESTION', NEW.question_id
  FROM follows f
  WHERE f.target_type='QUESTION' AND f.target_id=NEW.question_id
    AND f.deleted_at IS NULL AND f.user_id<>NEW.author_id
  ON CONFLICT DO NOTHING;

  INSERT INTO notifications(id,user_id,notification_type,title,body,target_type,target_id)
  SELECT md5('tanidik-answer:'||NEW.id||':'||f.user_id)::uuid,
         f.user_id,
         'FOLLOWED_TANIDIK_ANSWERED',
         'Takip ettiğin Tanıdık yanıt verdi',
         'Takip ettiğin Tanıdık yeni bir deneyim paylaştı.',
         'ANSWER', NEW.id
  FROM follows f
  WHERE NEW.answer_kind='ADMIN' AND f.target_type='TANIDIK' AND f.target_id=NEW.author_id
    AND f.deleted_at IS NULL AND f.user_id<>NEW.author_id
  ON CONFLICT DO NOTHING;
  RETURN NEW;
END $$;

CREATE TRIGGER answer_follow_notifications AFTER INSERT ON answers
  FOR EACH ROW EXECUTE FUNCTION notify_question_followers();
