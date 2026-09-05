package com.tanidikvar.api.profile.repository;

import com.tanidikvar.api.profile.entity.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProfileRepository {
    private final JdbcTemplate jdbc;
    public ProfileRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<UserProfile> find(UUID id) {
        return jdbc.query("SELECT * FROM user_profiles WHERE user_id=?", (rs,n) -> new UserProfile(
                rs.getObject("user_id",UUID.class),rs.getString("first_name"),rs.getString("last_name"),
                EducationStatus.valueOf(rs.getString("education_status")),rs.getObject("university_department_id",UUID.class),
                rs.getObject("graduation_year",Integer.class),rs.getString("biography"),rs.getString("occupation"),rs.getString("company"),
                rs.getTimestamp("deleted_at")==null?null:rs.getTimestamp("deleted_at").toInstant(),rs.getLong("version")),id).stream().findFirst();
    }
    public void save(UserProfile p, boolean exists) {
        if (!exists) jdbc.update("INSERT INTO user_profiles(user_id,first_name,last_name,education_status,university_department_id,graduation_year,biography,occupation,company) VALUES (?,?,?,?,?,?,?,?,?)",
                p.userId(),p.firstName(),p.lastName(),p.educationStatus().name(),p.universityDepartmentId(),p.graduationYear(),p.biography(),p.occupation(),p.company());
        else jdbc.update("UPDATE user_profiles SET first_name=?,last_name=?,education_status=?,university_department_id=?,graduation_year=?,biography=?,occupation=?,company=?,deleted_at=NULL,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE user_id=?",
                p.firstName(),p.lastName(),p.educationStatus().name(),p.universityDepartmentId(),p.graduationYear(),p.biography(),p.occupation(),p.company(),p.userId());
    }
}
