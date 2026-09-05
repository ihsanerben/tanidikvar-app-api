package com.tanidikvar.api.management.repository;
import com.tanidikvar.api.management.entity.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class ManagementRepository {
 private final JdbcTemplate jdbc;
 public ManagementRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private static Instant time(ResultSet r,String key)throws SQLException{var t=r.getTimestamp(key);return t==null?null:t.toInstant();}
 private static final String USERS="FROM users u LEFT JOIN user_profiles p ON p.user_id=u.id AND p.deleted_at IS NULL ";
 private static final String USER_SELECT="SELECT u.*,nullif(concat_ws(' ',p.first_name,p.last_name),'') name,p.education_status "+USERS;
 private ManagedUser mapUser(ResultSet r,int n)throws SQLException{return new ManagedUser(r.getObject("id",UUID.class),r.getString("email"),r.getString("name"),r.getString("authority"),r.getString("education_status"),r.getTimestamp("email_verified_at")!=null,time(r,"created_at"),time(r,"deleted_at"),r.getLong("version"));}
 public Optional<ManagedUser> user(UUID id){return jdbc.query(USER_SELECT+"WHERE u.id=?",this::mapUser,id).stream().findFirst();}
 private String userFilter(String status){return " WHERE position(search_fold(?) in search_fold(concat_ws(' ',u.email,p.first_name,p.last_name)))>0 "+(status.equals("ALL")?"":status.equals("HIDDEN")?"AND u.deleted_at IS NOT NULL ":"AND u.deleted_at IS NULL ");}
 public List<ManagedUser> users(String query,String status,int page,int size){return jdbc.query(USER_SELECT+userFilter(status)+"ORDER BY u.created_at DESC,u.id LIMIT ? OFFSET ?",this::mapUser,query,size,page*size);}
 public long userCount(String query,String status){return jdbc.queryForObject("SELECT count(*) "+USERS+userFilter(status),Long.class,query);}
 // The moderation read model includes hidden parents and owner-removed answers.
 private static final String CONTENT="""
  FROM (
   SELECT q.id,'QUESTION'::text kind,q.id question_id,q.title,q.body,q.author_id,q.deleted_at,
    q.deleted_at moderated_at,q.archived_at,false question_hidden,q.version,q.created_at
   FROM questions q
   UNION ALL
   SELECT a.id,a.answer_kind,a.question_id,q.title,a.body,a.author_id,a.deleted_at,
    a.moderated_at,q.archived_at,q.deleted_at IS NOT NULL,a.version,a.created_at
   FROM answers a JOIN questions q ON q.id=a.question_id
  ) c JOIN users u ON u.id=c.author_id
  LEFT JOIN user_profiles p ON p.user_id=u.id AND p.deleted_at IS NULL AND u.deleted_at IS NULL
  """;
 private static final String CONTENT_SELECT="SELECT c.*,coalesce(nullif(concat_ws(' ',p.first_name,p.last_name),''),'Katılımcı') author_name "+CONTENT;
 private ManagedContent content(ResultSet r,int n)throws SQLException{return new ManagedContent(r.getObject("id",UUID.class),r.getString("kind"),r.getObject("question_id",UUID.class),r.getString("title"),r.getString("body"),r.getString("author_name"),time(r,"deleted_at"),time(r,"moderated_at"),time(r,"archived_at"),r.getBoolean("question_hidden"),r.getLong("version"));}
 public Optional<ManagedContent> content(UUID id,String kind){return jdbc.query(CONTENT_SELECT+"WHERE c.id=? AND c.kind=?",this::content,id,kind).stream().findFirst();}
 private String contentFilter(String status){return " WHERE c.kind=? AND position(search_fold(?) in search_fold(concat_ws(' ',c.title,c.body)))>0 "+(status.equals("ALL")?"":status.equals("HIDDEN")?"AND c.moderated_at IS NOT NULL ":"AND c.moderated_at IS NULL AND c.deleted_at IS NULL AND NOT c.question_hidden ");}
 public List<ManagedContent> contents(String kind,String query,String status,int page,int size){return jdbc.query(CONTENT_SELECT+contentFilter(status)+"ORDER BY c.created_at DESC,c.id LIMIT ? OFFSET ?",this::content,kind,query,size,page*size);}
 public long contentCount(String kind,String query,String status){return jdbc.queryForObject("SELECT count(*) "+CONTENT+contentFilter(status),Long.class,kind,query);}
 public boolean lockQuestion(UUID id){return !jdbc.query("SELECT id FROM questions WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),id).isEmpty();}
 public void moderate(UUID id,String kind,boolean hidden){
  String sql=kind.equals("QUESTION")?"UPDATE questions SET deleted_at=CASE WHEN ? THEN clock_timestamp() ELSE NULL END,updated_at=clock_timestamp(),version=version+1 WHERE id=?":"UPDATE answers SET moderated_at=CASE WHEN ? THEN clock_timestamp() ELSE NULL END,updated_at=clock_timestamp(),version=version+1 WHERE id=?";
  jdbc.update(sql,hidden,id);
 }
 public void audit(UUID actor,String action,String type,UUID id,String reason){jdbc.update("INSERT INTO management_actions(id,actor_id,action,target_type,target_id,reason) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),actor,action,type,id,reason);}
 public List<ManagementAction> actions(int page,int size){return jdbc.query("SELECT * FROM management_actions WHERE deleted_at IS NULL ORDER BY occurred_at DESC,id LIMIT ? OFFSET ?",(r,n)->new ManagementAction(r.getObject("id",UUID.class),r.getObject("actor_id",UUID.class),r.getString("action"),r.getString("target_type"),r.getObject("target_id",UUID.class),r.getString("reason"),time(r,"occurred_at")),size,page*size);}
 public long actionCount(){return jdbc.queryForObject("SELECT count(*) FROM management_actions WHERE deleted_at IS NULL",Long.class);}
 public ManagementStats stats(){return jdbc.queryForObject("""
  SELECT
   (SELECT count(*) FROM users WHERE deleted_at IS NULL) active_users,
   (SELECT count(*) FROM users WHERE deleted_at IS NOT NULL) disabled_users,
   (SELECT count(*) FROM users WHERE deleted_at IS NULL AND authority='ADMIN') active_admins,
   (SELECT count(*) FROM admin_applications a JOIN users u ON u.id=a.applicant_id AND u.deleted_at IS NULL WHERE a.deleted_at IS NULL AND a.status='PENDING') pending_applications,
   (SELECT count(*) FROM questions WHERE deleted_at IS NULL AND archived_at IS NULL) active_questions,
   (SELECT count(*) FROM questions WHERE deleted_at IS NULL AND archived_at IS NOT NULL) archived_questions,
   (SELECT count(*) FROM questions WHERE deleted_at IS NOT NULL) hidden_questions,
   (SELECT count(*) FROM answers a JOIN questions q ON q.id=a.question_id AND q.deleted_at IS NULL WHERE a.deleted_at IS NULL AND a.moderated_at IS NULL AND a.answer_kind='COMMUNITY') community_answers,
   (SELECT count(*) FROM answers a JOIN questions q ON q.id=a.question_id AND q.deleted_at IS NULL WHERE a.deleted_at IS NULL AND a.moderated_at IS NULL AND a.answer_kind='ADMIN') admin_answers,
   (SELECT count(*) FROM question_likes l JOIN questions q ON q.id=l.question_id AND q.deleted_at IS NULL WHERE l.deleted_at IS NULL) likes,
   (SELECT count(*) FROM question_views v JOIN questions q ON q.id=v.question_id AND q.deleted_at IS NULL WHERE v.deleted_at IS NULL) views
  """,(r,n)->new ManagementStats(r.getLong("active_users"),r.getLong("disabled_users"),r.getLong("active_admins"),r.getLong("pending_applications"),r.getLong("active_questions"),r.getLong("archived_questions"),r.getLong("hidden_questions"),r.getLong("community_answers"),r.getLong("admin_answers"),r.getLong("likes"),r.getLong("views")));}
}
