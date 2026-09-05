-- Search folding is deliberately separate from catalog uniqueness normalization.
CREATE FUNCTION search_fold(value text) RETURNS text LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT translate(lower(translate(normalize(coalesce(value,''), NFKC),'İI','ii')),'çğıöşüâîû','cgiosuaiu')
$$;
CREATE INDEX idx_answers_discovery_period ON answers(published_at,question_id,answer_kind) WHERE deleted_at IS NULL;
