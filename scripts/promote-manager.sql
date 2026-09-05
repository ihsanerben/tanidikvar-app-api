-- Run with psql -v manager_email='your-verified-email'. No default account is created.
BEGIN;
WITH promoted AS (
    UPDATE users SET authority='MANAGER',updated_at=CURRENT_TIMESTAMP,version=version+1
    WHERE email=lower(trim(:'manager_email')) AND deleted_at IS NULL AND email_verified_at IS NOT NULL
      AND authority<>'MANAGER'
    RETURNING id
)
INSERT INTO management_actions(id,actor_id,action,target_type,target_id)
    SELECT gen_random_uuid(),id,'BOOTSTRAP_MANAGER','USER',id FROM promoted;
SELECT id,authority FROM users WHERE email=lower(trim(:'manager_email')) AND deleted_at IS NULL;
COMMIT;
