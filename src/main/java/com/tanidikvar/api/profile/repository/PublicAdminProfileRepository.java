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
 LEFT JOIN stored_files f ON f.owner_id=u.id AND f.purpose='AVATAR' AND f.upload_status='READY' AND f.deleted_at IS NULL
 """;
 private static final String SELECT="""
 SELECT u.id,concat_ws(' ',v.submitted_first_name,v.submitted_last_name) name,
 (u.authority='ADMIN' AND u.active_verification_application_id=v.id) active_admin,
 v.university_name,v.department_name,v.education_status,v.graduation_year,p.biography,p.occupation,p.company,f.id avatar_file_id,
 (SELECT count(*) FROM answers a JOIN questions q ON q.id=a.question_id AND q.deleted_at IS NULL WHERE a.author_id=u.id AND a.answer_kind='ADMIN' AND a.deleted_at IS NULL AND a.moderated_at IS NULL) answer_count
 """+FROM;
 private PublicAdminProfile map(ResultSet r,int n)throws SQLException {return new PublicAdminProfile(r.getObject("id",UUID.class),r.getString("name"),r.getBoolean("active_admin"),r.getString("university_name"),r.getString("department_name"),r.getString("education_status"),(Integer)r.getObject("graduation_year"),r.getString("biography"),r.getString("occupation"),r.getString("company"),r.getObject("avatar_file_id",UUID.class),r.getLong("answer_count"));}
 public Optional<PublicAdminProfile> find(UUID id){return jdbc.query(SELECT+" WHERE u.id=? AND u.deleted_at IS NULL",this::map,id).stream().findFirst();}
 private static final String SEARCH=" WHERE u.deleted_at IS NULL AND strpos(search_fold(concat_ws(' ',v.submitted_first_name,v.submitted_last_name)),search_fold(?))>0";
 public List<PublicAdminProfile> search(String query,int page,int size) {
  return jdbc.query(SELECT+SEARCH+" ORDER BY active_admin DESC,search_fold(concat_ws(' ',v.submitted_first_name,v.submitted_last_name)),u.id LIMIT ? OFFSET ?",this::map,query,size,page*size);
 }
 public long count(String query){return jdbc.queryForObject("SELECT count(*) "+FROM+SEARCH,Long.class,query);}
}
