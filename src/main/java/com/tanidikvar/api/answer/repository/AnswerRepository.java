package com.tanidikvar.api.answer.repository;
import com.tanidikvar.api.answer.entity.Answer;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class AnswerRepository {
    private final JdbcTemplate jdbc;
    public AnswerRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String FROM="""
        FROM answers a JOIN questions q ON q.id=a.question_id AND q.deleted_at IS NULL
        JOIN users u ON u.id=a.author_id
        LEFT JOIN user_profiles p ON p.user_id=u.id AND p.deleted_at IS NULL AND u.deleted_at IS NULL
        """;
    private static final String SELECT="SELECT a.*,nullif(concat_ws(' ',p.first_name,p.last_name),'') author_name "+FROM;
    private Instant time(ResultSet r,String key)throws SQLException {var t=r.getTimestamp(key);return t==null?null:t.toInstant();}
    private Answer map(ResultSet r,int n)throws SQLException {
        return new Answer(r.getObject("id",UUID.class),r.getObject("question_id",UUID.class),r.getObject("author_id",UUID.class),r.getString("author_name"),r.getString("body"),
                time(r,"published_at"),time(r,"edited_at"),time(r,"deleted_at"),time(r,"moderated_at"),r.getLong("version"));
    }
    public Optional<Answer> find(UUID id) { return jdbc.query(SELECT+" WHERE a.id=? AND a.answer_kind='COMMUNITY'",this::map,id).stream().findFirst(); }
    public Optional<Answer> own(UUID question,UUID actor) {return jdbc.query(SELECT+" WHERE a.question_id=? AND a.author_id=? AND a.answer_kind='COMMUNITY'",this::map,question,actor).stream().findFirst();}
    public List<Answer> list(UUID question,int page,int size) {
        return jdbc.query(SELECT+" WHERE a.question_id=? AND a.answer_kind='COMMUNITY' AND a.deleted_at IS NULL AND a.moderated_at IS NULL ORDER BY a.published_at,a.id LIMIT ? OFFSET ?",this::map,question,size,page*size);
    }
    public long count(UUID question) { return jdbc.queryForObject("SELECT count(*) FROM answers a JOIN questions q ON q.id=a.question_id AND q.deleted_at IS NULL WHERE a.question_id=? AND a.answer_kind='COMMUNITY' AND a.deleted_at IS NULL AND a.moderated_at IS NULL",Long.class,question); }
    public void create(UUID id,UUID question,UUID actor,String body) {jdbc.update("INSERT INTO answers(id,question_id,author_id,body) VALUES (?,?,?,?)",id,question,actor,body);}
    public void update(UUID id,String body) {jdbc.update("UPDATE answers SET body=?,edited_at=clock_timestamp(),updated_at=clock_timestamp(),version=version+1 WHERE id=?",body,id);}
    public void status(UUID id,boolean deleted) {jdbc.update("UPDATE answers SET deleted_at=CASE WHEN ? THEN clock_timestamp() ELSE NULL END,updated_at=clock_timestamp(),version=version+1 WHERE id=?",deleted,id);}
}
