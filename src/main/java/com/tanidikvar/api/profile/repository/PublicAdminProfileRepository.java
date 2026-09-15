package com.tanidikvar.api.profile.repository;
import com.tanidikvar.api.profile.entity.PublicAdminProfile;
import java.util.*;
import java.sql.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class PublicAdminProfileRepository {
 private final JdbcTemplate jdbc;
 public PublicAdminProfileRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private static final String FROM="""
 FROM users u JOIN user_profiles p ON p.user_id=u.id AND p.deleted_at IS NULL
 JOIN LATERAL (SELECT v.* FROM admin_applications v WHERE v.applicant_id=u.id AND v.status='APPROVED' AND v.deleted_at IS NULL ORDER BY (v.id=u.active_verification_application_id) DESC NULLS LAST,v.reviewed_at DESC,v.id LIMIT 1) v ON true
 LEFT JOIN universities university ON university.id=p.university_id
 LEFT JOIN departments department ON department.id=p.department_id
 LEFT JOIN stored_files f ON f.owner_id=u.id AND f.purpose='AVATAR' AND f.upload_status='READY' AND f.deleted_at IS NULL
 """;
 private static final String SELECT="""
 SELECT u.id,concat_ws(' ',p.first_name,p.last_name) name,
 (u.authority='TANIDIK' AND u.active_verification_application_id=v.id) active_admin,
 EXISTS(SELECT 1 FROM education_verifications ev WHERE ev.user_id=u.id AND ev.verified_at IS NOT NULL AND ev.deleted_at IS NULL) education_verified,
 university.name university_name,department.name department_name,p.education_status,p.class_year,p.graduation_year,p.biography,p.occupation,p.company,p.linkedin_url,p.portfolio_url,f.id avatar_file_id,u.created_at,
 (SELECT count(*) FROM answers a JOIN questions q ON q.id=a.question_id AND q.deleted_at IS NULL WHERE a.author_id=u.id AND a.answer_kind='TANIDIK' AND NOT a.anonymous AND a.deleted_at IS NULL AND a.moderated_at IS NULL) answer_count,
 (SELECT count(*) FROM answers a JOIN questions q ON q.id=a.question_id AND q.deleted_at IS NULL WHERE a.author_id=u.id AND a.answer_kind='COMMUNITY' AND a.deleted_at IS NULL AND a.moderated_at IS NULL) community_answer_count,
 (SELECT count(*) FROM answer_likes l JOIN answers a ON a.id=l.answer_id WHERE a.author_id=u.id AND l.deleted_at IS NULL AND a.deleted_at IS NULL AND a.moderated_at IS NULL) helpful_vote_count,
 (SELECT count(*) FROM questions q JOIN answers a ON a.id=q.best_answer_id WHERE a.author_id=u.id AND q.deleted_at IS NULL AND a.deleted_at IS NULL AND a.moderated_at IS NULL) best_answer_count,
 (SELECT count(DISTINCT l.user_id) FROM answer_likes l JOIN answers a ON a.id=l.answer_id WHERE a.author_id=u.id AND l.deleted_at IS NULL AND l.user_id<>u.id) helped_people_count
 """+FROM;
 private PublicAdminProfile map(ResultSet r,int n)throws SQLException {return new PublicAdminProfile(r.getObject("id",UUID.class),r.getString("name"),r.getBoolean("active_admin"),r.getBoolean("education_verified"),r.getString("university_name"),r.getString("department_name"),r.getString("education_status"),(Integer)r.getObject("class_year"),(Integer)r.getObject("graduation_year"),r.getString("biography"),r.getString("occupation"),r.getString("company"),r.getString("linkedin_url"),r.getString("portfolio_url"),r.getObject("avatar_file_id",UUID.class),r.getLong("answer_count"),r.getLong("community_answer_count"),r.getLong("helpful_vote_count"),r.getLong("best_answer_count"),r.getLong("helped_people_count"),r.getTimestamp("created_at").toInstant());}
 public Optional<PublicAdminProfile> find(UUID id){return jdbc.query(SELECT+" WHERE u.id=? AND u.deleted_at IS NULL",this::map,id).stream().findFirst();}
 private static final String SEARCH=" WHERE u.deleted_at IS NULL AND strpos(search_fold(concat_ws(' ',p.first_name,p.last_name)),search_fold(?))>0 AND (?::uuid IS NULL OR university.id=?) AND (?::uuid IS NULL OR department.id=?) AND (?='' OR p.education_status=?) AND (?::integer IS NULL OR p.class_year=?) AND (?::boolean IS NULL OR EXISTS(SELECT 1 FROM education_verifications ev WHERE ev.user_id=u.id AND ev.verified_at IS NOT NULL AND ev.deleted_at IS NULL)=?) AND (?='' OR EXISTS(SELECT 1 FROM answers ea JOIN questions eq ON eq.id=ea.question_id JOIN question_tags qt ON qt.question_id=eq.id JOIN tags et ON et.id=qt.tag_id WHERE ea.author_id=u.id AND ea.deleted_at IS NULL AND ea.moderated_at IS NULL AND strpos(search_fold(et.name),search_fold(?))>0))";
 private static final String ACTIVE=" AND u.authority='TANIDIK' AND u.active_verification_application_id=v.id";
 public List<PublicAdminProfile> search(String query,boolean activeOnly,UUID university,UUID department,String role,Integer classYear,Boolean verified,String expertise,int page,int size) {
  return jdbc.query(SELECT+SEARCH+(activeOnly?ACTIVE:"")+" ORDER BY active_admin DESC,search_fold(concat_ws(' ',p.first_name,p.last_name)),u.id LIMIT ? OFFSET ?",this::map,query,university,university,department,department,role,role,classYear,classYear,verified,verified,expertise,expertise,size,page*size);
 }
 public long count(String query,boolean activeOnly,UUID university,UUID department,String role,Integer classYear,Boolean verified,String expertise){return jdbc.queryForObject("SELECT count(*) "+FROM+SEARCH+(activeOnly?ACTIVE:""),Long.class,query,university,university,department,department,role,role,classYear,classYear,verified,verified,expertise,expertise);}
}
