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
 private static final String USERS="FROM users u LEFT JOIN user_profiles p ON p.user_id=u.id AND p.deleted_at IS NULL LEFT JOIN manager_profiles m ON m.user_id=u.id AND m.deleted_at IS NULL LEFT JOIN universities un ON un.id=p.university_id LEFT JOIN departments d ON d.id=p.department_id ";
 private static final String USER_SELECT="SELECT u.*,coalesce(nullif(concat_ws(' ',m.first_name,m.last_name),''),nullif(concat_ws(' ',p.first_name,p.last_name),'')) name,p.education_status,un.name university_name,d.name department_name "+USERS;
 private ManagedUser mapUser(ResultSet r,int n)throws SQLException{return new ManagedUser(r.getObject("id",UUID.class),r.getString("email"),r.getString("name"),r.getString("authority"),r.getString("education_status"),r.getString("university_name"),r.getString("department_name"),r.getTimestamp("email_verified_at")!=null,time(r,"created_at"),time(r,"last_login_at"),time(r,"deleted_at"),r.getLong("version"));}
 public Optional<ManagedUser> user(UUID id){return jdbc.query(USER_SELECT+"WHERE u.id=?",this::mapUser,id).stream().findFirst();}
 private String userFilter(String status){return " WHERE position(search_fold(?) in search_fold(concat_ws(' ',u.email,p.first_name,p.last_name,m.first_name,m.last_name)))>0 "+(status.equals("ALL")?"":status.equals("HIDDEN")?"AND u.deleted_at IS NOT NULL ":"AND u.deleted_at IS NULL ");}
 public List<ManagedUser> users(String query,String status,String authority,String educationStatus,String sort,int page,int size){String order=switch(sort){case "CREATED_ASC"->"u.created_at,u.id";case "LAST_LOGIN_DESC"->"u.last_login_at DESC NULLS LAST,u.id";case "LAST_LOGIN_ASC"->"u.last_login_at NULLS LAST,u.id";default->"u.created_at DESC,u.id";};return jdbc.query(USER_SELECT+userFilter(status)+" AND (?='' OR u.authority=?) AND (?='' OR p.education_status=?) ORDER BY "+order+" LIMIT ? OFFSET ?",this::mapUser,query,authority,authority,educationStatus,educationStatus,size,page*size);}
 public long userCount(String query,String status,String authority,String educationStatus){return jdbc.queryForObject("SELECT count(*) "+USERS+userFilter(status)+" AND (?='' OR u.authority=?) AND (?='' OR p.education_status=?)",Long.class,query,authority,authority,educationStatus,educationStatus);}
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
 private static final String CONTENT_SELECT="SELECT c.*,CASE WHEN p.user_id IS NULL THEN NULL ELSE c.author_id END author_id,coalesce(nullif(concat_ws(' ',p.first_name,p.last_name),''),'Katılımcı') author_name,CASE WHEN qp.user_id IS NULL THEN NULL ELSE q.author_id END question_author_id,coalesce(nullif(concat_ws(' ',qp.first_name,qp.last_name),''),'Katılımcı') question_author_name,(SELECT count(*) FROM question_views v WHERE v.question_id=c.question_id AND v.deleted_at IS NULL) view_count,(SELECT count(*) FROM question_likes l WHERE l.question_id=c.question_id AND l.deleted_at IS NULL) like_count,(SELECT count(*) FROM answers a WHERE a.question_id=c.question_id AND a.answer_kind='COMMUNITY' AND a.deleted_at IS NULL AND a.moderated_at IS NULL) community_answer_count,(SELECT count(*) FROM answers a WHERE a.question_id=c.question_id AND a.answer_kind='ADMIN' AND a.deleted_at IS NULL AND a.moderated_at IS NULL) admin_answer_count "+CONTENT+" JOIN questions q ON q.id=c.question_id JOIN users qu ON qu.id=q.author_id LEFT JOIN user_profiles qp ON qp.user_id=qu.id AND qp.deleted_at IS NULL AND qu.deleted_at IS NULL ";
 private ManagedContent content(ResultSet r,int n)throws SQLException{return new ManagedContent(r.getObject("id",UUID.class),r.getString("kind"),r.getObject("question_id",UUID.class),r.getObject("author_id",UUID.class),r.getString("title"),r.getString("body"),r.getString("author_name"),r.getObject("question_author_id",UUID.class),r.getString("question_author_name"),time(r,"created_at"),time(r,"deleted_at"),time(r,"moderated_at"),time(r,"archived_at"),r.getBoolean("question_hidden"),r.getLong("view_count"),r.getLong("like_count"),r.getLong("community_answer_count"),r.getLong("admin_answer_count"),r.getLong("version"));}
 public Optional<ManagedContent> content(UUID id,String kind){return jdbc.query(CONTENT_SELECT+"WHERE c.id=? AND c.kind=?",this::content,id,kind).stream().findFirst();}
 private String contentFilter(String status){return " WHERE c.kind=? AND position(search_fold(?) in search_fold(concat_ws(' ',c.title,c.body)))>0 AND (?::uuid IS NULL OR c.author_id=?::uuid) "+switch(status){case "ALL"->"";case "HIDDEN"->"AND c.moderated_at IS NOT NULL ";case "ARCHIVED"->"AND c.kind='QUESTION' AND c.archived_at IS NOT NULL AND c.moderated_at IS NULL ";default->"AND c.moderated_at IS NULL AND c.deleted_at IS NULL AND NOT c.question_hidden AND c.archived_at IS NULL ";};}
 public List<ManagedContent> contents(String kind,String query,String status,UUID authorId,int page,int size){return jdbc.query(CONTENT_SELECT+contentFilter(status)+"ORDER BY c.created_at DESC,c.id LIMIT ? OFFSET ?",this::content,kind,query,authorId,authorId,size,page*size);}
 public long contentCount(String kind,String query,String status,UUID authorId){return jdbc.queryForObject("SELECT count(*) "+CONTENT+contentFilter(status),Long.class,kind,query,authorId,authorId);}
 public boolean lockQuestion(UUID id){return !jdbc.query("SELECT id FROM questions WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),id).isEmpty();}
 public void moderate(UUID id,String kind,boolean hidden){
  String sql=kind.equals("QUESTION")?"UPDATE questions SET deleted_at=CASE WHEN ? THEN clock_timestamp() ELSE NULL END,updated_at=clock_timestamp(),version=version+1 WHERE id=?":"UPDATE answers SET moderated_at=CASE WHEN ? THEN clock_timestamp() ELSE NULL END,updated_at=clock_timestamp(),version=version+1 WHERE id=?";
  jdbc.update(sql,hidden,id);
 }
 public void editAnswer(UUID id,String body){jdbc.update("UPDATE answers SET body=?,edited_at=clock_timestamp(),updated_at=clock_timestamp(),version=version+1 WHERE id=?",body,id);}
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
 public List<ManagementAnalyticsPoint> analytics(java.time.LocalDate from,java.time.LocalDate to,java.time.ZoneId zone){
  var start=Timestamp.from(from.atStartOfDay(zone).toInstant());var until=Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant());
  return jdbc.query("""
   WITH bounds AS (SELECT ?::timestamptz start_at,?::timestamptz end_at),
   days AS (SELECT generate_series(?::date,?::date,interval '1 day')::date AS report_date),
   events AS (
    SELECT (created_at AT TIME ZONE 'Europe/Istanbul')::date AS report_date,'users' metric,count(*) amount FROM users,bounds WHERE created_at>=start_at AND created_at<end_at GROUP BY 1
    UNION ALL SELECT (created_at AT TIME ZONE 'Europe/Istanbul')::date,'questions',count(*) FROM questions,bounds WHERE created_at>=start_at AND created_at<end_at GROUP BY 1
    UNION ALL SELECT (published_at AT TIME ZONE 'Europe/Istanbul')::date,CASE answer_kind WHEN 'ADMIN' THEN 'admin_answers' ELSE 'community_answers' END,count(*) FROM answers,bounds WHERE published_at>=start_at AND published_at<end_at GROUP BY 1,2
    UNION ALL SELECT (viewed_at AT TIME ZONE 'Europe/Istanbul')::date,'views',count(*) FROM question_views,bounds WHERE viewed_at>=start_at AND viewed_at<end_at GROUP BY 1
    UNION ALL SELECT (first_liked_at AT TIME ZONE 'Europe/Istanbul')::date,'likes',count(*) FROM question_likes,bounds WHERE first_liked_at>=start_at AND first_liked_at<end_at GROUP BY 1
    UNION ALL SELECT (submitted_at AT TIME ZONE 'Europe/Istanbul')::date,'applications',count(*) FROM admin_applications,bounds WHERE submitted_at>=start_at AND submitted_at<end_at GROUP BY 1
    UNION ALL SELECT (reviewed_at AT TIME ZONE 'Europe/Istanbul')::date,CASE status WHEN 'APPROVED' THEN 'approved' ELSE 'rejected' END,count(*) FROM admin_applications,bounds WHERE reviewed_at>=start_at AND reviewed_at<end_at AND status IN ('APPROVED','REJECTED') GROUP BY 1,2
   )
   SELECT d.report_date,
    coalesce(sum(amount) FILTER(WHERE metric='users'),0) users,
    coalesce(sum(amount) FILTER(WHERE metric='questions'),0) questions,
    coalesce(sum(amount) FILTER(WHERE metric='community_answers'),0) community_answers,
    coalesce(sum(amount) FILTER(WHERE metric='admin_answers'),0) admin_answers,
    coalesce(sum(amount) FILTER(WHERE metric='views'),0) views,
    coalesce(sum(amount) FILTER(WHERE metric='likes'),0) likes,
    coalesce(sum(amount) FILTER(WHERE metric='applications'),0) applications,
    coalesce(sum(amount) FILTER(WHERE metric='approved'),0) approved_applications,
    coalesce(sum(amount) FILTER(WHERE metric='rejected'),0) rejected_applications
   FROM days d LEFT JOIN events e ON e.report_date=d.report_date GROUP BY d.report_date ORDER BY d.report_date
   """,(r,n)->new ManagementAnalyticsPoint(r.getObject("report_date",java.time.LocalDate.class),r.getLong("users"),r.getLong("questions"),r.getLong("community_answers"),r.getLong("admin_answers"),r.getLong("views"),r.getLong("likes"),r.getLong("applications"),r.getLong("approved_applications"),r.getLong("rejected_applications")),start,until,from,to);
 }
 public ManagerIdentity identity(UUID actor){return jdbc.queryForObject("SELECT u.email,m.first_name,m.last_name,coalesce(m.version,0) version FROM users u LEFT JOIN manager_profiles m ON m.user_id=u.id AND m.deleted_at IS NULL WHERE u.id=?",(r,n)->new ManagerIdentity(r.getString("first_name"),r.getString("last_name"),r.getString("email"),r.getLong("version")),actor);}
 public void saveIdentity(UUID actor,String first,String last){jdbc.update("INSERT INTO manager_profiles(user_id,first_name,last_name) VALUES (?,?,?) ON CONFLICT(user_id) DO UPDATE SET first_name=EXCLUDED.first_name,last_name=EXCLUDED.last_name,updated_at=clock_timestamp(),version=manager_profiles.version+1,deleted_at=NULL",actor,first,last);}
 public Optional<ManagementUserDetail> detail(UUID id){return user(id).map(user->jdbc.queryForObject("""
 SELECT p.first_name,p.last_name,p.university_id,un.name university_name,p.department_id,d.name department_name,p.graduation_year,f.id avatar_file_id,p.biography,p.occupation,p.company,p.linkedin_url,p.portfolio_url,u.active_verification_application_id,
 (SELECT count(*) FROM questions WHERE author_id=u.id) questions,
 (SELECT count(*) FROM answers WHERE author_id=u.id AND answer_kind='COMMUNITY') community_answers,
 (SELECT count(*) FROM answers WHERE author_id=u.id AND answer_kind='ADMIN') admin_answers
 FROM users u LEFT JOIN user_profiles p ON p.user_id=u.id AND p.deleted_at IS NULL
 LEFT JOIN stored_files f ON f.owner_id=u.id AND f.purpose='AVATAR' AND f.upload_status='READY' AND f.deleted_at IS NULL
 LEFT JOIN universities un ON un.id=p.university_id LEFT JOIN departments d ON d.id=p.department_id WHERE u.id=?
 """,(r,n)->new ManagementUserDetail(user,r.getString("first_name"),r.getString("last_name"),r.getObject("university_id",UUID.class),r.getString("university_name"),r.getObject("department_id",UUID.class),r.getString("department_name"),(Integer)r.getObject("graduation_year"),r.getObject("avatar_file_id",UUID.class),r.getString("biography"),r.getString("occupation"),r.getString("company"),r.getString("linkedin_url"),r.getString("portfolio_url"),r.getObject("active_verification_application_id",UUID.class),r.getLong("questions"),r.getLong("community_answers"),r.getLong("admin_answers")),id));}
 public ManagementClassification classification(UUID id){var tags=jdbc.query("SELECT tag_id FROM question_tags WHERE question_id=? AND deleted_at IS NULL ORDER BY tag_id",(r,n)->r.getObject(1,UUID.class),id);return jdbc.queryForObject("SELECT scope,university_id,department_id,version FROM questions WHERE id=?",(r,n)->new ManagementClassification(com.tanidikvar.api.question.entity.QuestionScope.valueOf(r.getString("scope")),r.getObject("university_id",UUID.class),r.getObject("department_id",UUID.class),tags,r.getLong("version")),id);}
 public void classify(UUID id,ManagementClassification c,String title,String body,boolean textChanged){jdbc.update("UPDATE questions SET title=?,body=?,scope=?,university_id=?,department_id=?,university_department_id=NULL,edited_at=CASE WHEN ? THEN clock_timestamp() ELSE edited_at END,updated_at=clock_timestamp(),version=version+1 WHERE id=?",title,body,c.scope().name(),c.universityId(),c.departmentId(),textChanged,id);
 jdbc.update("UPDATE question_tags SET deleted_at=clock_timestamp(),updated_at=clock_timestamp(),version=version+1 WHERE question_id=? AND deleted_at IS NULL AND NOT (tag_id=ANY(?))",id,c.tagIds().toArray(UUID[]::new));
 for(UUID tag:c.tagIds())jdbc.update("INSERT INTO question_tags(question_id,tag_id) VALUES (?,?) ON CONFLICT(question_id,tag_id) DO UPDATE SET deleted_at=NULL,updated_at=clock_timestamp(),version=question_tags.version+1 WHERE question_tags.deleted_at IS NOT NULL",id,tag);
 }
 public List<ManagedContent> questionAnswers(UUID id,int page,int size){return jdbc.query(CONTENT_SELECT+"WHERE c.question_id=? AND c.kind<>'QUESTION' ORDER BY c.created_at,c.id LIMIT ? OFFSET ?",this::content,id,size,page*size);}
 public long questionAnswerCount(UUID id){return jdbc.queryForObject("SELECT count(*) FROM answers WHERE question_id=?",Long.class,id);}
 public CatalogUsage usage(String kind,UUID id){
 String relation=switch(kind){case "UNIVERSITY"->"university_id=?";case "DEPARTMENT"->"department_id=?";case "TAG"->null;default->throw new IllegalArgumentException("Unsupported kind");};
 if(relation==null)return new CatalogUsage(0,jdbc.queryForObject("SELECT count(DISTINCT question_id) FROM question_tags WHERE tag_id=? AND deleted_at IS NULL",Long.class,id));
 long profiles=jdbc.queryForObject("SELECT count(*) FROM user_profiles WHERE "+relation,Long.class,id);
 long questions=jdbc.queryForObject("SELECT count(*) FROM questions WHERE "+relation,Long.class,id);
 return new CatalogUsage(profiles,questions);
 }
 private static final String ACTION_FROM="FROM management_actions a JOIN users u ON u.id=a.actor_id LEFT JOIN manager_profiles m ON m.user_id=u.id LEFT JOIN user_profiles p ON p.user_id=u.id ";
 private static final String ACTION_FILTER="WHERE a.deleted_at IS NULL AND position(search_fold(?) in search_fold(concat_ws(' ',a.action,a.reason,a.target_type,a.target_id::text,u.email,m.first_name,m.last_name,p.first_name,p.last_name)))>0 AND (?='' OR a.action=?) AND (?='' OR a.target_type=?) AND (?::timestamptz IS NULL OR a.occurred_at>=?) AND (?::timestamptz IS NULL OR a.occurred_at<?) ";
 private ManagementAction mapAction(ResultSet r,int n)throws SQLException{return new ManagementAction(r.getObject("id",UUID.class),r.getObject("actor_id",UUID.class),r.getString("action"),r.getString("target_type"),r.getObject("target_id",UUID.class),r.getString("reason"),time(r,"occurred_at"));}
 public List<ManagementAction> filteredActions(String q,String action,String type,Instant from,Instant until,int page,int size){var f=from==null?null:Timestamp.from(from);var u=until==null?null:Timestamp.from(until);return jdbc.query("SELECT a.* "+ACTION_FROM+ACTION_FILTER+"ORDER BY a.occurred_at DESC,a.id LIMIT ? OFFSET ?",this::mapAction,q,action,action,type,type,f,f,u,u,size,page*size);}
 public long filteredActionCount(String q,String action,String type,Instant from,Instant until){var f=from==null?null:Timestamp.from(from);var u=until==null?null:Timestamp.from(until);return jdbc.queryForObject("SELECT count(*) "+ACTION_FROM+ACTION_FILTER,Long.class,q,action,action,type,type,f,f,u,u);}
 public Optional<ManagementAction> action(UUID id){return jdbc.query("SELECT * FROM management_actions WHERE id=? AND deleted_at IS NULL",this::mapAction,id).stream().findFirst();}
 public void editUser(UUID id,String first,String last,String educationStatus,UUID universityId,UUID departmentId,Integer graduationYear,String biography,String occupation,String company,String linkedin,String portfolio){jdbc.update("INSERT INTO user_profiles(user_id,first_name,last_name,education_status,university_id,department_id,graduation_year,biography,occupation,company,linkedin_url,portfolio_url) VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET first_name=EXCLUDED.first_name,last_name=EXCLUDED.last_name,education_status=EXCLUDED.education_status,university_id=EXCLUDED.university_id,department_id=EXCLUDED.department_id,university_department_id=NULL,graduation_year=EXCLUDED.graduation_year,biography=EXCLUDED.biography,occupation=EXCLUDED.occupation,company=EXCLUDED.company,linkedin_url=EXCLUDED.linkedin_url,portfolio_url=EXCLUDED.portfolio_url,deleted_at=NULL,updated_at=clock_timestamp(),version=user_profiles.version+1",id,first,last,educationStatus,universityId,departmentId,graduationYear,biography,occupation,company,linkedin,portfolio);}
 public String actorName(UUID id){return jdbc.queryForObject("SELECT coalesce(nullif(concat_ws(' ',m.first_name,m.last_name),''),nullif(concat_ws(' ',p.first_name,p.last_name),''),u.email) "+"FROM users u LEFT JOIN manager_profiles m ON m.user_id=u.id LEFT JOIN user_profiles p ON p.user_id=u.id WHERE u.id=?",String.class,id);}
}
