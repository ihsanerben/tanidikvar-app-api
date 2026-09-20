CREATE TABLE program_academic_details (
    id uuid PRIMARY KEY,
    program_id uuid NOT NULL REFERENCES programs(id) ON DELETE RESTRICT,
    academic_unit_id uuid REFERENCES academic_units(id) ON DELETE RESTRICT,
    professor_count integer CHECK (professor_count >= 0),
    associate_professor_count integer CHECK (associate_professor_count >= 0),
    doctor_faculty_member_count integer CHECK (doctor_faculty_member_count >= 0),
    research_assistant_count integer CHECK (research_assistant_count >= 0),
    accreditation_code varchar(64),
    accreditation_description varchar(500),
    minimum_success_rank integer CHECK (minimum_success_rank >= 0),
    tyc_qualified boolean,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_program_academic_details UNIQUE NULLS NOT DISTINCT (program_id, academic_unit_id)
);

INSERT INTO program_academic_details (
    id, program_id, academic_unit_id, professor_count, associate_professor_count,
    doctor_faculty_member_count, research_assistant_count, accreditation_code,
    accreditation_description, minimum_success_rank, tyc_qualified
)
SELECT gen_random_uuid(), program_id, academic_unit_id, max(professor_count),
       max(associate_professor_count), max(doctor_faculty_member_count),
       max(research_assistant_count), max(accreditation_code),
       max(accreditation_description), max(minimum_success_rank), bool_or(tyc_qualified)
FROM admission_options
WHERE professor_count IS NOT NULL OR associate_professor_count IS NOT NULL
   OR doctor_faculty_member_count IS NOT NULL OR research_assistant_count IS NOT NULL
   OR accreditation_code IS NOT NULL OR accreditation_description IS NOT NULL
   OR minimum_success_rank IS NOT NULL OR tyc_qualified IS NOT NULL
GROUP BY program_id, academic_unit_id;

ALTER TABLE admission_options
    DROP COLUMN professor_count,
    DROP COLUMN associate_professor_count,
    DROP COLUMN doctor_faculty_member_count,
    DROP COLUMN research_assistant_count,
    DROP COLUMN accreditation_code,
    DROP COLUMN accreditation_description,
    DROP COLUMN minimum_success_rank,
    DROP COLUMN tyc_qualified;

CREATE INDEX idx_program_academic_details_program ON program_academic_details(program_id);
