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
 private static final String USERS="FROM users u LEFT JOIN user_profiles p ON p.user_id=u.id AND p.deleted_at IS NULL LEFT JOIN manager_profiles m ON m.user_id=u.id AND m.deleted_at IS NULL ";
 private static final String USER_SELECT="SELECT u.*,coalesce(nullif(concat_ws(' ',m.first_name,m.last_name),''),nullif(concat_ws(' ',p.first_name,p.last_name),'')) name,p.education_status "+USERS;
 private ManagedUser mapUser(ResultSet r,int n)throws SQLException{return new ManagedUser(r.getObject("id",UUID.class),r.getString("email"),r.getString("name"),r.getString("authority"),r.getString("education_status"),r.getTimestamp("email_verified_at")!=null,time(r,"created_at"),time(r,"deleted_at"),r.getLong("version"));}
 public Optional<ManagedUser> user(UUID id){return jdbc.query(USER_SELECT+"WHERE u.id=?",this::mapUser,id).stream().findFirst();}
 private String userFilter(String status){return " WHERE position(search_fold(?) in search_fold(concat_ws(' ',u.email,p.first_name,p.last_name,m.first_name,m.last_name)))>0 "+(status.equals("ALL")?"":status.equals("HIDDEN")?"AND u.deleted_at IS NOT NULL ":"AND u.deleted_at IS NULL ");}
 public List<ManagedUser> users(String query,String status,String authority,int page,int size){return jdbc.query(USER_SELECT+userFilter(status)+" AND (?='' OR u.authority=?) ORDER BY u.created_at DESC,u.id LIMIT ? OFFSET ?",this::mapUser,query,authority,authority,size,page*size);}
 public long userCount(String query,String status,String authority){return jdbc.queryForObject("SELECT count(*) "+USERS+userFilter(status)+" AND (?='' OR u.authority=?)",Long.class,query,authority,authority);}
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
 public ManagerIdentity identity(UUID actor){return jdbc.queryForObject("SELECT u.email,m.first_name,m.last_name,coalesce(m.version,0) version FROM users u LEFT JOIN manager_profiles m ON m.user_id=u.id AND m.deleted_at IS NULL WHERE u.id=?",(r,n)->new ManagerIdentity(r.getString("first_name"),r.getString("last_name"),r.getString("email"),r.getLong("version")),actor);}
 public void saveIdentity(UUID actor,String first,String last){jdbc.update("INSERT INTO manager_profiles(user_id,first_name,last_name) VALUES (?,?,?) ON CONFLICT(user_id) DO UPDATE SET first_name=EXCLUDED.first_name,last_name=EXCLUDED.last_name,updated_at=clock_timestamp(),version=manager_profiles.version+1,deleted_at=NULL",actor,first,last);}
 public Optional<ManagementUserDetail> detail(UUID id){return user(id).map(user->jdbc.queryForObject("""
 SELECT un.name university_name,d.name department_name,p.graduation_year,u.active_verification_application_id,
 (SELECT count(*) FROM questions WHERE author_id=u.id) questions,
 (SELECT count(*) FROM answers WHERE author_id=u.id AND answer_kind='COMMUNITY') community_answers,
 (SELECT count(*) FROM answers WHERE author_id=u.id AND answer_kind='ADMIN') admin_answers
 FROM users u LEFT JOIN user_profiles p ON p.user_id=u.id AND p.deleted_at IS NULL
 LEFT JOIN university_departments ud ON ud.id=p.university_department_id
 LEFT JOIN universities un ON un.id=ud.university_id LEFT JOIN departments d ON d.id=ud.department_id WHERE u.id=?
 """,(r,n)->new ManagementUserDetail(user,r.getString("university_name"),r.getString("department_name"),(Integer)r.getObject("graduation_year"),r.getObject("active_verification_application_id",UUID.class),r.getLong("questions"),r.getLong("community_answers"),r.getLong("admin_answers")),id));}
 public ManagementClassification classification(UUID id){var tags=jdbc.query("SELECT tag_id FROM question_tags WHERE question_id=? AND deleted_at IS NULL ORDER BY tag_id",(r,n)->r.getObject(1,UUID.class),id);return jdbc.queryForObject("SELECT scope,university_id,university_department_id,version FROM questions WHERE id=?",(r,n)->new ManagementClassification(com.tanidikvar.api.question.entity.QuestionScope.valueOf(r.getString("scope")),r.getObject("university_id",UUID.class),r.getObject("university_department_id",UUID.class),tags,r.getLong("version")),id);}
 public void classify(UUID id,ManagementClassification c){jdbc.update("UPDATE questions SET scope=?,university_id=?,university_department_id=?,updated_at=clock_timestamp(),version=version+1 WHERE id=?",c.scope().name(),c.universityId(),c.universityDepartmentId(),id);
 jdbc.update("UPDATE question_tags SET deleted_at=clock_timestamp(),updated_at=clock_timestamp(),version=version+1 WHERE question_id=? AND deleted_at IS NULL AND NOT (tag_id=ANY(?))",id,c.tagIds().toArray(UUID[]::new));
 for(UUID tag:c.tagIds())jdbc.update("INSERT INTO question_tags(question_id,tag_id) VALUES (?,?) ON CONFLICT(question_id,tag_id) DO UPDATE SET deleted_at=NULL,updated_at=clock_timestamp(),version=question_tags.version+1 WHERE question_tags.deleted_at IS NOT NULL",id,tag);
 }
 public List<ManagedContent> questionAnswers(UUID id,int page,int size){return jdbc.query(CONTENT_SELECT+"WHERE c.question_id=? AND c.kind<>'QUESTION' ORDER BY c.created_at,c.id LIMIT ? OFFSET ?",this::content,id,size,page*size);}
 public long questionAnswerCount(UUID id){return jdbc.queryForObject("SELECT count(*) FROM answers WHERE question_id=?",Long.class,id);}
 public CatalogUsage usage(String kind,UUID id){
 String relation=switch(kind){case "UNIVERSITY"->"ud.university_id=?";case "DEPARTMENT"->"ud.department_id=?";case "UNIVERSITY_DEPARTMENT"->"ud.id=?";case "TAG"->null;default->throw new IllegalArgumentException("Unsupported kind");};
 if(relation==null)return new CatalogUsage(0,jdbc.queryForObject("SELECT count(DISTINCT question_id) FROM question_tags WHERE tag_id=? AND deleted_at IS NULL",Long.class,id));
 long profiles=jdbc.queryForObject("SELECT count(*) FROM user_profiles p JOIN university_departments ud ON ud.id=p.university_department_id WHERE "+relation,Long.class,id);
 String question="SELECT count(*) FROM questions q LEFT JOIN university_departments ud ON ud.id=q.university_department_id WHERE "+relation;
 long questions=kind.equals("UNIVERSITY")?jdbc.queryForObject(question+" OR q.university_id=?",Long.class,id,id):jdbc.queryForObject(question,Long.class,id);
 return new CatalogUsage(profiles,questions);
 }
 private static final String ACTION_FROM="FROM management_actions a JOIN users u ON u.id=a.actor_id LEFT JOIN manager_profiles m ON m.user_id=u.id LEFT JOIN user_profiles p ON p.user_id=u.id ";
 private static final String ACTION_FILTER="WHERE a.deleted_at IS NULL AND position(search_fold(?) in search_fold(concat_ws(' ',a.action,a.reason,a.target_type,a.target_id::text,u.email,m.first_name,m.last_name,p.first_name,p.last_name)))>0 AND (?='' OR a.action=?) AND (?='' OR a.target_type=?) ";
 private ManagementAction mapAction(ResultSet r,int n)throws SQLException{return new ManagementAction(r.getObject("id",UUID.class),r.getObject("actor_id",UUID.class),r.getString("action"),r.getString("target_type"),r.getObject("target_id",UUID.class),r.getString("reason"),time(r,"occurred_at"));}
 public List<ManagementAction> filteredActions(String q,String action,String type,int page,int size){return jdbc.query("SELECT a.* "+ACTION_FROM+ACTION_FILTER+"ORDER BY a.occurred_at DESC,a.id LIMIT ? OFFSET ?",this::mapAction,q,action,action,type,type,size,page*size);}
 public long filteredActionCount(String q,String action,String type){return jdbc.queryForObject("SELECT count(*) "+ACTION_FROM+ACTION_FILTER,Long.class,q,action,action,type,type);}
 public Optional<ManagementAction> action(UUID id){return jdbc.query("SELECT * FROM management_actions WHERE id=? AND deleted_at IS NULL",this::mapAction,id).stream().findFirst();}
 public String actorName(UUID id){return jdbc.queryForObject("SELECT coalesce(nullif(concat_ws(' ',m.first_name,m.last_name),''),nullif(concat_ws(' ',p.first_name,p.last_name),''),u.email) "+"FROM users u LEFT JOIN manager_profiles m ON m.user_id=u.id LEFT JOIN user_profiles p ON p.user_id=u.id WHERE u.id=?",String.class,id);}
}
