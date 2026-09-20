UPDATE admission_statistics
SET minimum_score = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE minimum_score = 0;
