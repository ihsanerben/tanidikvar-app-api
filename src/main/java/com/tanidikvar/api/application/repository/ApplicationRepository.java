package com.tanidikvar.api.application.repository;
import com.tanidikvar.api.application.entity.AdminApplication;
import com.tanidikvar.api.profile.dto.ProfileResponse;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
@Repository
public class ApplicationRepository {
 private final JdbcTemplate jdbc;
 public ApplicationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private static final String SELECT="SELECT a.*, (u.authority='ADMIN' AND u.active_verification_application_id=a.id) AS active_verification FROM admin_applications a JOIN users u ON u.id=a.applicant_id WHERE a.deleted_at IS NULL AND u.deleted_at IS NULL";
 private final RowMapper<AdminApplication> row=(r,n)->new AdminApplication(r.getObject("id",UUID.class),r.getObject("applicant_id",UUID.class),r.getObject("request_id",UUID.class),r.getString("submitted_first_name"),r.getString("submitted_last_name"),r.getString("education_status"),r.getObject("university_department_id",UUID.class),r.getString("university_name"),r.getString("department_name"),(Integer)r.getObject("graduation_year"),r.getString("occupation"),r.getString("company"),r.getObject("document_file_id",UUID.class),r.getString("document_sha256"),r.getLong("profile_version"),r.getString("status"),r.getTimestamp("submitted_at").toInstant(),r.getObject("reviewed_by",UUID.class),r.getTimestamp("reviewed_at")==null?null:r.getTimestamp("reviewed_at").toInstant(),r.getString("rejection_reason"),r.getLong("version"),r.getBoolean("active_verification"));
 public Optional<AdminApplication> find(UUID id){return jdbc.query(SELECT+" AND a.id=?",row,id).stream().findFirst();}
 public Optional<AdminApplication> request(UUID owner,UUID request){return jdbc.query(SELECT+" AND a.applicant_id=? AND a.request_id=?",row,owner,request).stream().findFirst();}
 public boolean pending(UUID owner){return jdbc.queryForObject("SELECT count(*) FROM admin_applications WHERE applicant_id=? AND status='PENDING' AND deleted_at IS NULL",Long.class,owner)>0;}
 public void insert(UUID id,UUID owner,UUID request,ProfileResponse p,UUID file,String hash){
  jdbc.update("INSERT INTO admin_applications(id,applicant_id,request_id,submitted_first_name,submitted_last_name,education_status,university_department_id,university_name,department_name,graduation_year,occupation,company,document_file_id,document_sha256,profile_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,owner,request,p.firstName(),p.lastName(),p.educationStatus().name(),p.education().id(),p.education().universityName(),p.education().departmentName(),p.graduationYear(),p.occupation(),p.company(),file,hash,p.version());
 }
 public List<AdminApplication> list(UUID owner,String status,int page,int size){return jdbc.query(SELECT+" AND (?::uuid IS NULL OR a.applicant_id=?::uuid) AND (?::text IS NULL OR a.status=?::text) ORDER BY a.submitted_at DESC,a.id LIMIT ? OFFSET ?",row,owner,owner,status,status,size,page*size);}
 public long count(UUID owner,String status){return jdbc.queryForObject("SELECT count(*) FROM admin_applications a JOIN users u ON u.id=a.applicant_id WHERE a.deleted_at IS NULL AND u.deleted_at IS NULL AND (?::uuid IS NULL OR a.applicant_id=?::uuid) AND (?::text IS NULL OR a.status=?::text)",Long.class,owner,owner,status,status);}
 public void decide(UUID id,UUID actor,String status,String reason){jdbc.update("UPDATE admin_applications SET status=?,reviewed_by=?,reviewed_at=clock_timestamp(),rejection_reason=?,updated_at=clock_timestamp(),version=version+1 WHERE id=? AND status='PENDING'",status,actor,reason,id);}
 public List<UUID> pendingIds(UUID owner){return jdbc.query("SELECT id FROM admin_applications WHERE applicant_id=? AND status='PENDING' AND deleted_at IS NULL",(r,n)->r.getObject(1,UUID.class),owner);}
 public void audit(UUID actor,String action,String type,UUID target,String reason){jdbc.update("INSERT INTO management_actions(id,actor_id,action,target_type,target_id,reason) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),actor,action,type,target,reason);}
 public Optional<AdminApplication> managerFind(UUID id){return jdbc.query(SELECT.replace(" AND u.deleted_at IS NULL","")+" AND a.id=?",row,id).stream().findFirst();}
 public List<AdminApplication> managerHistory(UUID owner,int page,int size){return jdbc.query(SELECT.replace(" AND u.deleted_at IS NULL","")+" AND a.applicant_id=? ORDER BY a.submitted_at DESC,a.id LIMIT ? OFFSET ?",row,owner,size,page*size);}
 public long managerHistoryCount(UUID owner){return jdbc.queryForObject("SELECT count(*) FROM admin_applications WHERE applicant_id=? AND deleted_at IS NULL",Long.class,owner);}
}

