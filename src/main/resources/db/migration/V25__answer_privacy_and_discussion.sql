ALTER TABLE answers ADD COLUMN anonymous boolean NOT NULL DEFAULT false;
ALTER TABLE answers ADD CONSTRAINT chk_anonymous_tanidik CHECK(NOT anonymous OR answer_kind='ADMIN');
ALTER TABLE questions ADD COLUMN best_answer_id uuid REFERENCES answers(id) ON DELETE RESTRICT;
CREATE TABLE answer_comments(id uuid PRIMARY KEY,answer_id uuid NOT NULL REFERENCES answers(id) ON DELETE RESTRICT,author_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,body varchar(2000) NOT NULL CHECK(length(trim(body)) BETWEEN 2 AND 2000),created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,deleted_at timestamptz,version bigint NOT NULL DEFAULT 0);
CREATE INDEX idx_answer_comments_answer ON answer_comments(answer_id,created_at,id) WHERE deleted_at IS NULL;
CREATE TRIGGER answer_comments_no_delete BEFORE DELETE OR TRUNCATE ON answer_comments FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
CREATE TABLE question_templates(id uuid PRIMARY KEY,category varchar(30) NOT NULL,title varchar(200) NOT NULL,body varchar(1000),scope_hint varchar(30) NOT NULL,created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,deleted_at timestamptz);
INSERT INTO question_templates(id,category,title,body,scope_hint) VALUES
(md5('template-education')::uuid,'EĞİTİM','Bu programda derslerin uygulama ve teori dengesi nasıl?',NULL,'UNIVERSITY_DEPARTMENT'),
(md5('template-campus')::uuid,'KAMPÜS','Kampüste günlük yaşam ve sosyal imkânlar nasıl?',NULL,'UNIVERSITY'),
(md5('template-career')::uuid,'KARİYER','Mezuniyet sonrası iş ve staj fırsatları nasıl?',NULL,'UNIVERSITY_DEPARTMENT'),
(md5('template-prep')::uuid,'HAZIRLIK','Hazırlık eğitimi ve muafiyet sınavı hakkında ne bilmeliyim?',NULL,'UNIVERSITY_DEPARTMENT'),
(md5('template-erasmus')::uuid,'ERASMUS','Erasmus ve değişim olanaklarından yararlanmak kolay mı?',NULL,'UNIVERSITY'),
(md5('template-dorm')::uuid,'YURT','Yurt ve barınma seçenekleri hakkında güncel deneyimler neler?',NULL,'UNIVERSITY'),
(md5('template-cost')::uuid,'MALİYET','Aylık öğrenci giderleri ve şehir maliyeti ne düzeyde?',NULL,'UNIVERSITY'),
(md5('template-professors')::uuid,'HOCALAR','Hocalara ulaşmak ve akademik destek almak kolay mı?',NULL,'UNIVERSITY_DEPARTMENT');
CREATE TRIGGER question_templates_no_delete BEFORE DELETE OR TRUNCATE ON question_templates FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
