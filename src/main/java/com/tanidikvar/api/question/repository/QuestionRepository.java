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
        LEFT JOIN stored_files f ON f.owner_id=a.id AND f.purpose='AVATAR' AND f.upload_status='READY' AND f.deleted_at IS NULL
        LEFT JOIN universities u ON u.id=q.university_id
        LEFT JOIN departments d ON d.id=q.department_id
        """;
    private static final String SELECT="SELECT q.*,concat_ws(' ',p.first_name,p.last_name) author_name,u.name university_name,d.name department_name,f.id avatar_file_id,p.education_status,(a.authority='TANIDIK' AND EXISTS(SELECT 1 FROM admin_applications v WHERE v.id=a.active_verification_application_id AND v.applicant_id=a.id AND v.status='APPROVED' AND v.deleted_at IS NULL)) active_admin "+FROM;
    private Instant time(ResultSet r,String key)throws SQLException { var t=r.getTimestamp(key);return t==null?null:t.toInstant(); }
    private Question map(ResultSet r,int n)throws SQLException {
        String name=r.getString("author_name");
        return new Question(r.getObject("id",UUID.class),r.getObject("author_id",UUID.class),r.getString("title"),r.getString("body"),
                QuestionScope.valueOf(r.getString("scope")),r.getObject("university_id",UUID.class),r.getObject("program_id",UUID.class),r.getObject("department_id",UUID.class),
                time(r,"created_at"),time(r,"edited_at"),time(r,"archived_at"),time(r,"deleted_at"),r.getLong("version"),
                name==null||name.isBlank()?null:name,r.getString("university_name"),r.getString("department_name"),r.getObject("avatar_file_id",UUID.class),r.getString("education_status"),r.getBoolean("active_admin"),r.getObject("best_answer_id",UUID.class));
    }
    public Optional<Question> find(UUID id,boolean lock) {
        return jdbc.query(SELECT+" WHERE q.id=:id AND q.deleted_at IS NULL"+(lock?" FOR UPDATE OF q":""),Map.of("id",id),this::map).stream().findFirst();
    }
    public Optional<UUID> existing(UUID actor,UUID requestId) {
        return jdbc.query("SELECT id FROM questions WHERE author_id=:actor AND request_id=:request",Map.of("actor",actor,"request",requestId),(r,n)->r.getObject(1,UUID.class)).stream().findFirst();
    }
    private Map<String,Object> parameters(UUID id,QuestionContent c) {
        var p=new HashMap<String,Object>();p.put("id",id);p.put("title",c.title());p.put("body",c.body());p.put("scope",c.scope().name());
        p.put("university",c.universityId());p.put("program",c.programId());p.put("department",c.departmentId());return p;
    }
    public void create(UUID id,UUID actor,UUID request,QuestionContent c) {
        var p=parameters(id,c);p.put("actor",actor);p.put("request",request);
        jdbc.update("INSERT INTO questions(id,author_id,request_id,title,body,scope,university_id,program_id,department_id,university_department_id) VALUES (:id,:actor,:request,:title,:body,:scope,:university,:program,:department,NULL)",p);
    }
    public void update(UUID id,QuestionContent c) {
        jdbc.update("UPDATE questions SET title=:title,body=:body,scope=:scope,university_id=:university,program_id=:program,department_id=:department,university_department_id=NULL,edited_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=:id",parameters(id,c));
    }
    public void archive(UUID id) { jdbc.update("UPDATE questions SET archived_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=:id",Map.of("id",id)); }
    public void restore(UUID id) { jdbc.update("UPDATE questions SET archived_at=NULL,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=:id",Map.of("id",id)); }
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
        if(p.containsKey("archived"))sql+=(Boolean.TRUE.equals(p.get("archived"))?" AND q.archived_at IS NOT NULL":" AND q.archived_at IS NULL");
        if(p.containsKey("scope"))sql+=" AND q.scope=:scope";
        if(p.containsKey("university"))sql+=" AND u.id=:university";
        if(p.containsKey("tag"))sql+=" AND EXISTS (SELECT 1 FROM question_tags qt JOIN tags t ON t.id=qt.tag_id AND t.deleted_at IS NULL WHERE qt.question_id=q.id AND qt.tag_id=:tag AND qt.deleted_at IS NULL)";
        if(p.containsKey("department"))sql+=" AND q.department_id=:department";
        if(p.containsKey("program"))sql+=" AND q.program_id=:program";
        if(p.containsKey("city"))sql+=" AND u.deleted_at IS NULL AND strpos(search_fold(coalesce(u.city,'')),search_fold(:city))>0";
        if(p.containsKey("answered"))sql+=Boolean.TRUE.equals(p.get("answered"))
            ?" AND EXISTS (SELECT 1 FROM answers qa WHERE qa.question_id=q.id AND qa.deleted_at IS NULL AND qa.moderated_at IS NULL)"
            :" AND NOT EXISTS (SELECT 1 FROM answers qa WHERE qa.question_id=q.id AND qa.deleted_at IS NULL AND qa.moderated_at IS NULL)";
        if(p.containsKey("verifiedAnswer")) {
            String verified="""
                EXISTS (SELECT 1 FROM answers va JOIN users vu ON vu.id=va.author_id AND vu.deleted_at IS NULL
                  WHERE va.question_id=q.id AND va.deleted_at IS NULL AND va.moderated_at IS NULL AND
                  (EXISTS (SELECT 1 FROM education_verifications ev WHERE ev.user_id=vu.id AND ev.verified_at IS NOT NULL AND ev.deleted_at IS NULL)
                   OR EXISTS (SELECT 1 FROM admin_applications av WHERE av.applicant_id=vu.id AND av.status='APPROVED' AND av.deleted_at IS NULL)))
                """;
            sql+=Boolean.TRUE.equals(p.get("verifiedAnswer"))?" AND "+verified:" AND NOT "+verified;
        }
        if(p.containsKey("admin"))sql+=" "+"""
            AND EXISTS (SELECT 1 FROM answers aa JOIN users au ON au.id=aa.author_id AND au.deleted_at IS NULL
              JOIN user_profiles ap ON ap.user_id=au.id AND ap.deleted_at IS NULL
              WHERE aa.question_id=q.id AND aa.author_id=:admin AND aa.answer_kind='TANIDIK' AND aa.deleted_at IS NULL AND aa.moderated_at IS NULL
              AND EXISTS (SELECT 1 FROM admin_applications av WHERE av.applicant_id=au.id AND av.status='APPROVED' AND av.deleted_at IS NULL))
            """;
        if(p.containsKey("query"))sql+=" "+"""
            AND (strpos(search_fold(q.title),search_fold(:query))>0 OR strpos(search_fold(q.body),search_fold(:query))>0
              OR (u.deleted_at IS NULL AND strpos(search_fold(u.name),search_fold(:query))>0)
              OR (d.deleted_at IS NULL AND strpos(search_fold(d.name),search_fold(:query))>0)
              OR EXISTS (SELECT 1 FROM question_tags qt JOIN tags t ON t.id=qt.tag_id AND t.deleted_at IS NULL
                WHERE qt.question_id=q.id AND qt.deleted_at IS NULL AND strpos(search_fold(t.name),search_fold(:query))>0))
            """;
        return sql;
    }
    public List<Question> list(Map<String,Object> filters,String sort,int page,int size) {
        var p=new HashMap<>(filters);p.put("limit",size);p.put("offset",page*size);
        String order=switch(sort){case "OLDEST"->"q.created_at ASC,q.id ASC";case "MOST_VIEWED"->"(SELECT count(*) FROM question_views v WHERE v.question_id=q.id AND v.deleted_at IS NULL) DESC,q.created_at DESC,q.id DESC";case "MOST_LIKED"->"(SELECT count(*) FROM question_likes l WHERE l.question_id=q.id AND l.deleted_at IS NULL) DESC,q.created_at DESC,q.id DESC";case "MOST_COMMENTED"->"(SELECT count(*) FROM answers a2 WHERE a2.question_id=q.id AND a2.deleted_at IS NULL AND a2.moderated_at IS NULL) DESC,q.created_at DESC,q.id DESC";default->"q.created_at DESC,q.id DESC";};
        return jdbc.query(SELECT+where(filters)+" ORDER BY "+order+" LIMIT :limit OFFSET :offset",p,this::map);
    }
    public long count(Map<String,Object> filters) { return jdbc.queryForObject("SELECT count(*) "+FROM+where(filters),filters,Long.class); }
    private String popularCte(Map<String,Object> filters) {
        return "WITH eligible AS (SELECT q.id "+FROM+where(filters)+"), "+"""
            events AS (
                SELECT v.question_id,v.viewed_at happened,:viewWeight weight FROM question_views v JOIN eligible e ON e.id=v.question_id
                  WHERE v.deleted_at IS NULL AND v.viewed_at>=:since AND v.viewed_at<:until
                UNION ALL
                SELECT l.question_id,l.first_liked_at,:likeWeight FROM question_likes l JOIN eligible e ON e.id=l.question_id
                  WHERE l.deleted_at IS NULL AND l.first_liked_at>=:since AND l.first_liked_at<:until
                UNION ALL
                SELECT a.question_id,a.published_at,CASE WHEN a.answer_kind='TANIDIK' THEN :adminWeight ELSE :communityWeight END
                  FROM answers a JOIN eligible e ON e.id=a.question_id
                  WHERE a.deleted_at IS NULL AND a.moderated_at IS NULL AND a.published_at>=:since AND a.published_at<:until
            ), scores AS (
                SELECT question_id,sum(weight*(1-EXTRACT(EPOCH FROM (CAST(:until AS timestamptz)-happened))/(2*:seconds))) score
                FROM events GROUP BY question_id
            )
            """;
    }
    public List<Question> popular(Map<String,Object> filters,int page,int size) {
        var p=new HashMap<>(filters);p.put("limit",size);p.put("offset",page*size);
        return jdbc.query(popularCte(filters)+SELECT+" JOIN scores s ON s.question_id=q.id ORDER BY s.score DESC,q.created_at DESC,q.id DESC LIMIT :limit OFFSET :offset",p,this::map);
    }
    public long popularCount(Map<String,Object> filters) {return jdbc.queryForObject(popularCte(filters)+"SELECT count(*) FROM scores",filters,Long.class);}

}
