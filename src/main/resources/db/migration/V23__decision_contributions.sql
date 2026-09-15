CREATE TABLE context_metrics (
 id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 university_id uuid NOT NULL REFERENCES universities(id) ON DELETE RESTRICT,
 university_department_id uuid REFERENCES university_departments(id) ON DELETE RESTRICT,
 metric_key varchar(50) NOT NULL CHECK(metric_key IN ('WEEKLY_STUDY_HOURS','ATTENDANCE_LEVEL','PROJECT_INTENSITY','EXAM_INTENSITY','ENGLISH_PERCENT','GROUP_WORK_PERCENT','CAMPUS_HOURS','MONTHLY_HOUSING_COST','MONTHLY_TRANSPORT_COST','MONTHLY_FOOD_COST','JOB_SEARCH_MONTHS')),
 numeric_value numeric(12,2) NOT NULL CHECK(numeric_value>=0),verified boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,deleted_at timestamptz,version bigint NOT NULL DEFAULT 0,
 UNIQUE NULLS NOT DISTINCT(user_id,university_id,university_department_id,metric_key)
);
CREATE INDEX idx_context_metrics_scope ON context_metrics(university_id,university_department_id,metric_key) WHERE deleted_at IS NULL;
CREATE TRIGGER context_metrics_no_delete BEFORE DELETE OR TRUNCATE ON context_metrics FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();

CREATE TABLE structured_experiences (
 id uuid PRIMARY KEY,author_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 university_id uuid NOT NULL REFERENCES universities(id) ON DELETE RESTRICT,
 university_department_id uuid REFERENCES university_departments(id) ON DELETE RESTRICT,
 template_type varchar(40) NOT NULL CHECK(template_type IN ('WHY_I_CHOSE','WISH_I_KNEW','EXPECTATION_REALITY','CHOOSE_AGAIN','NEW_STUDENT_GUIDE')),
 title varchar(200) NOT NULL CHECK(length(trim(title))>=10),body varchar(5000) NOT NULL CHECK(length(trim(body))>=20),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,deleted_at timestamptz,version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_structured_experiences_scope ON structured_experiences(university_id,university_department_id,created_at DESC,id) WHERE deleted_at IS NULL;
CREATE TRIGGER structured_experiences_no_delete BEFORE DELETE OR TRUNCATE ON structured_experiences FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
