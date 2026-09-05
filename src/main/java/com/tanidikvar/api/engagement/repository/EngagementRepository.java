package com.tanidikvar.api.engagement.repository;
import com.tanidikvar.api.engagement.entity.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class EngagementRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public EngagementRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public QuestionLike like(UUID question,UUID actor) {
        return jdbc.query("SELECT deleted_at IS NULL AS liked,version FROM question_likes WHERE question_id=:q AND user_id=:u",Map.of("q",question,"u",actor),
            (r,n)->new QuestionLike(r.getBoolean("liked"),r.getLong("version"))).stream().findFirst().orElse(new QuestionLike(false,0));
    }
    public void setLike(UUID question,UUID actor,boolean liked,long version) {
        var p=Map.of("q",question,"u",actor,"liked",liked);
        if(version==0) jdbc.update("INSERT INTO question_likes(question_id,user_id) VALUES (:q,:u)",p);
        else jdbc.update("UPDATE question_likes SET deleted_at=CASE WHEN :liked THEN NULL ELSE clock_timestamp() END,updated_at=clock_timestamp(),version=version+1 WHERE question_id=:q AND user_id=:u",p);
    }
    public UUID view(UUID question,UUID event) {
        var p=Map.of("q",question,"event",event);
        jdbc.update("INSERT INTO question_views(opening_event_id,question_id) VALUES (:event,:q) ON CONFLICT(opening_event_id) DO NOTHING",p);
        return jdbc.queryForObject("SELECT question_id FROM question_views WHERE opening_event_id=:event",p,UUID.class);
    }
    public Map<UUID,QuestionStatistics> statistics(Collection<UUID> ids) {
        if(ids.isEmpty())return Map.of();
        var result=new HashMap<UUID,QuestionStatistics>();
        jdbc.query("""
            SELECT q.id,
              (SELECT count(*) FROM question_views v WHERE v.question_id=q.id AND v.deleted_at IS NULL) AS views,
              (SELECT count(*) FROM question_likes l WHERE l.question_id=q.id AND l.deleted_at IS NULL) AS likes,
              (SELECT count(*) FROM answers a WHERE a.question_id=q.id AND a.deleted_at IS NULL AND a.answer_kind='COMMUNITY') AS community,
              (SELECT count(*) FROM answers a WHERE a.question_id=q.id AND a.deleted_at IS NULL AND a.answer_kind='ADMIN') AS admins
            FROM questions q WHERE q.id IN (:ids) AND q.deleted_at IS NULL
            """,Map.of("ids",ids),(org.springframework.jdbc.core.RowCallbackHandler)r->result.put(r.getObject("id",UUID.class),new QuestionStatistics(r.getLong("views"),r.getLong("likes"),r.getLong("community"),r.getLong("admins"))));
        return result;
    }
}
