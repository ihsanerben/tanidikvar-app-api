ALTER TABLE admission_options
    ADD COLUMN professor_count integer CHECK (professor_count >= 0),
    ADD COLUMN associate_professor_count integer CHECK (associate_professor_count >= 0),
    ADD COLUMN doctor_faculty_member_count integer CHECK (doctor_faculty_member_count >= 0),
    ADD COLUMN research_assistant_count integer CHECK (research_assistant_count >= 0),
    ADD COLUMN accreditation_code varchar(64),
    ADD COLUMN accreditation_description varchar(500),
    ADD COLUMN annual_fee numeric(14,2) CHECK (annual_fee >= 0),
    ADD COLUMN minimum_success_rank integer CHECK (minimum_success_rank >= 0),
    ADD COLUMN tyc_qualified boolean;

UPDATE admission_options SET
    professor_count=NULLIF(source_payload->>'prof','')::integer,
    associate_professor_count=NULLIF(source_payload->>'doc','')::integer,
    doctor_faculty_member_count=NULLIF(source_payload->>'dou','')::integer,
    research_assistant_count=NULLIF(source_payload->>'arGor','')::integer,
    accreditation_code=NULLIF(source_payload->>'akreditasyon',''),
    accreditation_description=NULLIF(source_payload->>'akreditasyonAck',''),
    annual_fee=NULLIF(source_payload->>'ucret','')::numeric,
    minimum_success_rank=NULLIF(source_payload->>'minBasariSirasi','')::integer,
    tyc_qualified=CASE WHEN source_payload ? 'tyc' THEN source_payload->>'tyc'='*' END
WHERE source_payload IS NOT NULL;
