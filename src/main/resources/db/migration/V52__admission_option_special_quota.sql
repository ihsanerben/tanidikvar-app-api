ALTER TABLE admission_options
    ADD COLUMN special_quota_type varchar(120);

UPDATE admission_options
SET special_quota_type=NULLIF(source_payload->>'birimEkTuru','')
WHERE source_payload IS NOT NULL;
