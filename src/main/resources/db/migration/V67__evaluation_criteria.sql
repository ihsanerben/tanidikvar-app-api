CREATE TABLE evaluation_ratings (
 evaluation_id uuid NOT NULL REFERENCES evaluations(id) ON DELETE RESTRICT,
 criterion_key varchar(40) NOT NULL CHECK(criterion_key IN ('GENERAL','EDUCATION','ACADEMIC_STAFF','CAMPUS','TRANSPORT','HOUSING','CAREER','STUDENT_SERVICES')),
 rating smallint NOT NULL CHECK(rating BETWEEN 1 AND 5),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(evaluation_id,criterion_key)
);
-- Historical free text does not reliably identify a criterion. Preserve it as general feedback.
INSERT INTO evaluation_ratings(evaluation_id,criterion_key,rating,created_at,updated_at)
SELECT id,'GENERAL',rating,created_at,updated_at FROM evaluations;
CREATE TRIGGER evaluation_ratings_no_delete BEFORE DELETE OR TRUNCATE ON evaluation_ratings FOR EACH STATEMENT EXECUTE FUNCTION reject_physical_delete();
