package com.tanidikvar.api.profile.repository;
import com.tanidikvar.api.profile.entity.PublicProfile;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class PublicProfileRepository {
 private final JdbcTemplate jdbc;
 public PublicProfileRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Optional<PublicProfile> find(UUID id){return jdbc.query("""
 SELECT u.id,concat_ws(' ',p.first_name,p.last_name) name,u.email,CASE WHEN u.authority='ADMIN' THEN 'ADMIN' ELSE p.education_status END role,
 p.education_status,un.name university_name,d.name department_name,p.graduation_year,p.biography,p.occupation,p.company,p.linkedin_url,p.portfolio_url,f.id avatar_file_id
 FROM users u JOIN user_profiles p ON p.user_id=u.id AND p.deleted_at IS NULL
 LEFT JOIN university_departments ud ON ud.id=p.university_department_id
 LEFT JOIN universities un ON un.id=ud.university_id LEFT JOIN departments d ON d.id=ud.department_id
 LEFT JOIN stored_files f ON f.owner_id=u.id AND f.purpose='AVATAR' AND f.upload_status='READY' AND f.deleted_at IS NULL
 WHERE u.id=? AND u.deleted_at IS NULL AND u.authority<>'MANAGER'
 """,(r,n)->new PublicProfile(r.getObject("id",UUID.class),r.getString("name"),r.getString("email"),r.getString("role"),r.getString("education_status"),r.getString("university_name"),r.getString("department_name"),r.getObject("graduation_year",Integer.class),r.getString("biography"),r.getString("occupation"),r.getString("company"),r.getString("linkedin_url"),r.getString("portfolio_url"),r.getObject("avatar_file_id",UUID.class)),id).stream().findFirst();}
}
