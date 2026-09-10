ALTER TABLE users ADD COLUMN last_login_at timestamptz;
UPDATE users SET last_login_at=created_at WHERE last_login_at IS NULL;

ALTER TABLE admin_applications ADD COLUMN cover_letter varchar(1000) DEFAULT 'Bu başvuru eski sürümde ön yazı alınmadan gönderildi.';
UPDATE admin_applications SET cover_letter='Bu başvuru eski sürümde ön yazı alınmadan gönderildi.' WHERE cover_letter IS NULL;
ALTER TABLE admin_applications ALTER COLUMN cover_letter SET NOT NULL;
ALTER TABLE admin_applications ADD CONSTRAINT admin_applications_cover_letter_check CHECK(length(trim(cover_letter)) BETWEEN 20 AND 1000);

CREATE TABLE answer_likes (
 answer_id uuid NOT NULL REFERENCES answers(id) ON DELETE RESTRICT,
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted_at timestamptz,
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 PRIMARY KEY(answer_id,user_id)
);
CREATE INDEX idx_answer_likes_visible ON answer_likes(answer_id,created_at) WHERE deleted_at IS NULL;

CREATE TABLE question_reports (
 id uuid PRIMARY KEY,
 question_id uuid NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
 reporter_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 reason varchar(1000) NOT NULL CHECK(length(trim(reason)) BETWEEN 10 AND 1000),
 status varchar(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','RESOLVED','DISMISSED')),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reviewed_by uuid REFERENCES users(id) ON DELETE RESTRICT,
 reviewed_at timestamptz,
 resolution_reason varchar(1000),
 deleted_at timestamptz,
 updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 UNIQUE(question_id,reporter_id)
);
CREATE INDEX idx_question_reports_management ON question_reports(status,created_at DESC,id);
