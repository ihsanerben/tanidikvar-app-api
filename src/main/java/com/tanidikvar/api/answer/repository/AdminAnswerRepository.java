package com.tanidikvar.api.answer.repository;
import com.tanidikvar.api.answer.entity.AdminAnswer;
import com.tanidikvar.api.answer.dto.AssignmentResponse;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class AdminAnswerRepository {
 private final JdbcTemplate jdbc;
 public AdminAnswerRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private static final String FROM="""
 FROM answers a JOIN questions q ON q.id=a.question_id AND q.deleted_at IS NULL
 JOIN users u ON u.id=a.author_id
 LEFT JOIN user_profiles p ON p.user_id=u.id AND p.deleted_at IS NULL AND u.deleted_at IS NULL
 JOIN admin_applications v ON v.id=a.verification_application_id
 LEFT JOIN universities university ON university.id=p.university_id
 LEFT JOIN departments department ON department.id=p.department_id
 LEFT JOIN stored_files f ON f.owner_id=u.id AND f.purpose='AVATAR' AND f.upload_status='READY' AND f.deleted_at IS NULL
 WHERE a.answer_kind='TANIDIK'
 """;
 private static final String SELECT="""
 SELECT a.*,q.title question_title,CASE WHEN p.user_id IS NOT NULL THEN concat_ws(' ',p.first_name,p.last_name) END author_name,
 (u.authority='TANIDIK' AND EXISTS(SELECT 1 FROM admin_applications current_v WHERE current_v.id=u.active_verification_application_id AND current_v.applicant_id=u.id AND current_v.status='APPROVED' AND current_v.deleted_at IS NULL)) active_admin,
 EXISTS(SELECT 1 FROM education_verifications ev WHERE ev.user_id=u.id AND ev.verified_at IS NOT NULL AND ev.deleted_at IS NULL) education_verified,
 university.name university_name,department.name department_name,p.education_status,p.graduation_year,f.id avatar_file_id,p.occupation,p.company,(SELECT count(*) FROM answer_likes l WHERE l.answer_id=a.id AND l.deleted_at IS NULL) like_count
 """+FROM;
 private Instant time(ResultSet r,String key)throws SQLException{var t=r.getTimestamp(key);return t==null?null:t.toInstant();}
 private AdminAnswer map(ResultSet r,int n)throws SQLException{return new AdminAnswer(r.getObject("id",UUID.class),r.getObject("question_id",UUID.class),r.getString("question_title"),r.getObject("author_id",UUID.class),r.getString("author_name"),r.getBoolean("active_admin"),r.getBoolean("education_verified"),r.getBoolean("anonymous"),r.getString("university_name"),r.getString("department_name"),r.getString("education_status"),(Integer)r.getObject("graduation_year"),r.getObject("avatar_file_id",UUID.class),r.getString("occupation"),r.getString("company"),r.getString("body"),time(r,"published_at"),time(r,"edited_at"),time(r,"deleted_at"),time(r,"moderated_at"),r.getLong("like_count"),r.getLong("version"));}
 public Optional<AdminAnswer> find(UUID id){return jdbc.query(SELECT+" AND a.id=?",this::map,id).stream().findFirst();}
 public Optional<AdminAnswer> own(UUID q,UUID owner){return jdbc.query(SELECT+" AND a.question_id=? AND a.author_id=?",this::map,q,owner).stream().findFirst();}
 public List<AdminAnswer> list(UUID question,UUID author,boolean includeRemoved,String scope,int page,int size){return jdbc.query(SELECT+" AND (?::uuid IS NULL OR a.question_id=?::uuid) AND (?::uuid IS NULL OR (a.author_id=?::uuid AND (? OR NOT a.anonymous))) AND (? OR (a.deleted_at IS NULL AND a.moderated_at IS NULL)) AND (?::varchar IS NULL OR q.scope=?) ORDER BY like_count DESC,a.published_at DESC,a.id LIMIT ? OFFSET ?",this::map,question,question,author,author,includeRemoved,includeRemoved,scope,scope,size,page*size);}
 public long count(UUID question,UUID author,boolean includeRemoved,String scope){return jdbc.queryForObject("SELECT count(*) "+FROM+" AND (?::uuid IS NULL OR a.question_id=?::uuid) AND (?::uuid IS NULL OR (a.author_id=?::uuid AND (? OR NOT a.anonymous))) AND (? OR (a.deleted_at IS NULL AND a.moderated_at IS NULL)) AND (?::varchar IS NULL OR q.scope=?)",Long.class,question,question,author,author,includeRemoved,includeRemoved,scope,scope);}
 public long daily(UUID author,Instant start,Instant end){return jdbc.queryForObject("SELECT count(*) FROM answers WHERE author_id=? AND answer_kind='TANIDIK' AND published_at>=? AND published_at<?",Long.class,author,Timestamp.from(start),Timestamp.from(end));}
 public void create(UUID id,UUID q,UUID owner,UUID verification,String body,Instant published,boolean anonymous){jdbc.update("INSERT INTO answers(id,question_id,author_id,answer_kind,verification_application_id,body,published_at,anonymous) VALUES (?,?,?,'TANIDIK',?,?,?,?)",id,q,owner,verification,body,Timestamp.from(published),anonymous);}
 public void update(UUID id,String body){jdbc.update("UPDATE answers SET body=?,edited_at=clock_timestamp(),updated_at=clock_timestamp(),version=version+1 WHERE id=?",body,id);}
 public void status(UUID id,boolean removed){jdbc.update("UPDATE answers SET deleted_at=CASE WHEN ? THEN clock_timestamp() ELSE NULL END,updated_at=clock_timestamp(),version=version+1 WHERE id=?",removed,id);}
 public AssignmentResponse assignment(UUID q,UUID owner){return jdbc.query("SELECT q.id question_id,q.title,q.archived_at,s.assigned_at,s.deleted_at,s.version FROM questions q LEFT JOIN question_assignments s ON s.question_id=q.id AND s.admin_id=? WHERE q.id=? AND q.deleted_at IS NULL",(r,n)->new AssignmentResponse(q,r.getString("title"),r.getObject("version")!=null&&r.getTimestamp("deleted_at")==null,r.getObject("version")==null?0:r.getLong("version"),time(r,"assigned_at"),time(r,"archived_at")),owner,q).getFirst();}
 public void assign(UUID q,UUID owner,boolean assigned,long version){
  if(version==0){if(assigned)jdbc.update("INSERT INTO question_assignments(id,question_id,admin_id) VALUES (?,?,?)",UUID.randomUUID(),q,owner);}
  else jdbc.update("UPDATE question_assignments SET cancelled_at=CASE WHEN ? THEN NULL ELSE clock_timestamp() END,deleted_at=CASE WHEN ? THEN NULL ELSE clock_timestamp() END,assigned_at=CASE WHEN ? THEN clock_timestamp() ELSE assigned_at END,updated_at=clock_timestamp(),version=version+1 WHERE question_id=? AND admin_id=?",assigned,assigned,assigned,q,owner);
 }
 public List<AssignmentResponse> assignments(UUID owner,int page,int size){return jdbc.query("SELECT s.*,q.title,q.archived_at FROM question_assignments s JOIN questions q ON q.id=s.question_id AND q.deleted_at IS NULL WHERE s.admin_id=? AND s.deleted_at IS NULL ORDER BY s.assigned_at DESC,s.id LIMIT ? OFFSET ?",(r,n)->new AssignmentResponse(r.getObject("question_id",UUID.class),r.getString("title"),true,r.getLong("version"),time(r,"assigned_at"),time(r,"archived_at")),owner,size,page*size);}
 public long assignmentCount(UUID owner){return jdbc.queryForObject("SELECT count(*) FROM question_assignments s JOIN questions q ON q.id=s.question_id AND q.deleted_at IS NULL WHERE s.admin_id=? AND s.deleted_at IS NULL",Long.class,owner);}
}
