package com.tanidikvar.api.question.repository;
import com.tanidikvar.api.question.entity.*;
import com.tanidikvar.api.question.dto.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class QuestionRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public QuestionRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String FROM="""
        FROM questions q JOIN users a ON a.id=q.author_id
        LEFT JOIN user_profiles p ON p.user_id=a.id AND p.deleted_at IS NULL AND a.deleted_at IS NULL
        LEFT JOIN university_departments ud ON ud.id=q.university_department_id
        LEFT JOIN universities u ON u.id=coalesce(q.university_id,ud.university_id)
        LEFT JOIN departments d ON d.id=ud.department_id
        """;
    private static final String SELECT="SELECT q.*,concat_ws(' ',p.first_name,p.last_name) author_name,u.id display_university_id,u.name university_name,d.id department_id,d.name department_name "+FROM;
    private Instant time(ResultSet r,String key)throws SQLException { var t=r.getTimestamp(key);return t==null?null:t.toInstant(); }
    private Question map(ResultSet r,int n)throws SQLException {
        String name=r.getString("author_name");
        return new Question(r.getObject("id",UUID.class),r.getObject("author_id",UUID.class),r.getString("title"),r.getString("body"),
                QuestionScope.valueOf(r.getString("scope")),r.getObject("university_id",UUID.class),r.getObject("university_department_id",UUID.class),
                time(r,"created_at"),time(r,"edited_at"),time(r,"archived_at"),time(r,"deleted_at"),r.getLong("version"),
                name==null||name.isBlank()?null:name,r.getObject("display_university_id",UUID.class),r.getString("university_name"),r.getObject("department_id",UUID.class),r.getString("department_name"));
    }
    public Optional<Question> find(UUID id,boolean lock) {
        return jdbc.query(SELECT+" WHERE q.id=:id AND q.deleted_at IS NULL"+(lock?" FOR UPDATE OF q":""),Map.of("id",id),this::map).stream().findFirst();
    }
    public Optional<UUID> existing(UUID actor,UUID requestId) {
        return jdbc.query("SELECT id FROM questions WHERE author_id=:actor AND request_id=:request",Map.of("actor",actor,"request",requestId),(r,n)->r.getObject(1,UUID.class)).stream().findFirst();
    }
    private Map<String,Object> parameters(UUID id,QuestionContent c) {
        var p=new HashMap<String,Object>();p.put("id",id);p.put("title",c.title());p.put("body",c.body());p.put("scope",c.scope().name());
        p.put("university",c.universityId());p.put("education",c.universityDepartmentId());return p;
    }
    public void create(UUID id,UUID actor,UUID request,QuestionContent c) {
        var p=parameters(id,c);p.put("actor",actor);p.put("request",request);
        jdbc.update("INSERT INTO questions(id,author_id,request_id,title,body,scope,university_id,university_department_id) VALUES (:id,:actor,:request,:title,:body,:scope,:university,:education)",p);
    }
    public void update(UUID id,QuestionContent c) {
        jdbc.update("UPDATE questions SET title=:title,body=:body,scope=:scope,university_id=:university,university_department_id=:education,edited_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=:id",parameters(id,c));
    }
    public void archive(UUID id) { jdbc.update("UPDATE questions SET archived_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=:id",Map.of("id",id)); }
    public void tags(UUID id,List<UUID> tags) {
        var p=new HashMap<String,Object>();p.put("id",id);p.put("tags",tags);
        jdbc.update("UPDATE question_tags SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE question_id=:id AND deleted_at IS NULL"+(tags.isEmpty()?"":" AND tag_id NOT IN (:tags)"),p);
        for(UUID tag:tags) jdbc.update("""
            INSERT INTO question_tags(question_id,tag_id) VALUES (:id,:tag)
            ON CONFLICT(question_id,tag_id) DO UPDATE SET deleted_at=NULL,updated_at=CURRENT_TIMESTAMP,version=question_tags.version+1
            WHERE question_tags.deleted_at IS NOT NULL
            """,Map.of("id",id,"tag",tag));
    }
    public Map<UUID,List<QuestionTagResponse>> tags(List<UUID> ids) {
        Map<UUID,List<QuestionTagResponse>> result=new HashMap<>();
        if(ids.isEmpty())return result;
        jdbc.query("SELECT qt.question_id,t.id,t.name,(t.deleted_at IS NULL) available FROM question_tags qt JOIN tags t ON t.id=qt.tag_id WHERE qt.question_id IN (:ids) AND qt.deleted_at IS NULL ORDER BY t.normalized_name,t.id",Map.of("ids",ids),r->{
            result.computeIfAbsent(r.getObject("question_id",UUID.class),k->new ArrayList<>()).add(new QuestionTagResponse(r.getObject("id",UUID.class),r.getString("name"),r.getBoolean("available")));
        });return result;
    }
    private String where(Map<String,Object> p) {
        String sql=" WHERE q.deleted_at IS NULL";
        if(p.containsKey("actor"))sql+=" AND q.author_id=:actor";else sql+=" AND q.archived_at IS NULL";
        if(p.containsKey("scope"))sql+=" AND q.scope=:scope";
        if(p.containsKey("university"))sql+=" AND u.id=:university";
        if(p.containsKey("education"))sql+=" AND q.university_department_id=:education";
        if(p.containsKey("tag"))sql+=" AND EXISTS (SELECT 1 FROM question_tags qt JOIN tags t ON t.id=qt.tag_id AND t.deleted_at IS NULL WHERE qt.question_id=q.id AND qt.tag_id=:tag AND qt.deleted_at IS NULL)";
        return sql;
    }
    public List<Question> list(Map<String,Object> filters,int page,int size) {
        var p=new HashMap<>(filters);p.put("limit",size);p.put("offset",page*size);
        return jdbc.query(SELECT+where(filters)+" ORDER BY q.created_at DESC,q.id DESC LIMIT :limit OFFSET :offset",p,this::map);
    }
    public long count(Map<String,Object> filters) { return jdbc.queryForObject("SELECT count(*) "+FROM+where(filters),filters,Long.class); }
}
