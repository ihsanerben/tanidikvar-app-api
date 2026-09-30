package com.tanidikvar.api.profile.repository;

import com.tanidikvar.api.profile.dto.ProfileContributionSummaryResponse;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProfileContributionSummaryRepository {
    private final JdbcTemplate jdbc;

    public ProfileContributionSummaryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ProfileContributionSummaryResponse get(UUID userId) {
        return jdbc.queryForObject("""
            SELECT
              (SELECT count(*) FROM questions WHERE author_id=? AND deleted_at IS NULL),
              (SELECT count(*) FROM answers WHERE author_id=? AND deleted_at IS NULL AND moderated_at IS NULL)
                + (SELECT count(*) FROM answer_comments WHERE author_id=? AND deleted_at IS NULL),
              (SELECT count(*) FROM structured_experiences WHERE author_id=? AND deleted_at IS NULL),
              (SELECT count(*) FROM polls WHERE author_id=? AND deleted_at IS NULL)
            """, (row, index) -> new ProfileContributionSummaryResponse(row.getLong(1), row.getLong(2), row.getLong(3), row.getLong(4)), userId, userId, userId, userId, userId);
    }

    public ProfileContributionSummaryResponse getPublic(UUID userId) {
        return jdbc.queryForObject("""
            SELECT
              (SELECT count(*) FROM questions WHERE author_id=? AND deleted_at IS NULL AND archived_at IS NULL),
              (SELECT count(*) FROM answers a JOIN questions q ON q.id=a.question_id
                 WHERE a.author_id=? AND a.deleted_at IS NULL AND a.moderated_at IS NULL AND NOT a.anonymous
                   AND q.deleted_at IS NULL AND q.archived_at IS NULL)
                + (SELECT count(*) FROM answer_comments c JOIN answers a ON a.id=c.answer_id
                   JOIN questions q ON q.id=a.question_id
                   WHERE c.author_id=? AND c.deleted_at IS NULL AND a.deleted_at IS NULL
                     AND a.moderated_at IS NULL AND q.deleted_at IS NULL AND q.archived_at IS NULL),
              (SELECT count(*) FROM structured_experiences WHERE author_id=? AND deleted_at IS NULL),
              (SELECT count(*) FROM polls WHERE author_id=? AND deleted_at IS NULL)
            """, (row, index) -> new ProfileContributionSummaryResponse(row.getLong(1), row.getLong(2), row.getLong(3), row.getLong(4)), userId, userId, userId, userId, userId);
    }
}
