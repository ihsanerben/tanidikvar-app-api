package com.tanidikvar.api.catalog.repository;

import com.tanidikvar.api.catalog.entity.*;
import com.tanidikvar.api.catalog.dto.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;
    public CatalogRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private CatalogEntry entry(ResultSet rs,int n) throws SQLException {
        return new CatalogEntry(rs.getObject("id",UUID.class),rs.getString("name"),
                rs.getTimestamp("deleted_at")==null?null:rs.getTimestamp("deleted_at").toInstant(),rs.getLong("version"));
    }
    private static final String UNIVERSITY_COLUMNS="u.id,u.name,u.city,u.institution_type,u.description,u.website_url,u.logo_url,u.accent_primary,u.accent_soft,u.accent_foreground,(SELECT count(DISTINCT p.id) FROM programs p JOIN program_families pf ON pf.id=p.program_family_id AND pf.deleted_at IS NULL JOIN admission_options ao ON ao.program_id=p.id AND ao.deleted_at IS NULL WHERE p.university_id=u.id AND p.deleted_at IS NULL) program_count,(SELECT count(*) FROM questions q WHERE q.university_id=u.id AND q.deleted_at IS NULL AND q.archived_at IS NULL) question_count,(SELECT count(*) FROM user_profiles up JOIN users account ON account.id=up.user_id AND account.authority='TANIDIK' AND account.deleted_at IS NULL WHERE up.university_id=u.id AND up.deleted_at IS NULL) tanidik_count,u.deleted_at,u.version";
    private UniversityResponse university(ResultSet r,int n)throws SQLException{return new UniversityResponse(r.getObject("id",UUID.class),r.getString("name"),r.getString("city"),r.getString("institution_type"),r.getString("description"),r.getString("website_url"),r.getString("logo_url"),r.getString("accent_primary"),r.getString("accent_soft"),r.getString("accent_foreground"),r.getLong("program_count"),r.getLong("question_count"),r.getLong("tanidik_count"),r.getTimestamp("deleted_at")==null?null:r.getTimestamp("deleted_at").toInstant(),r.getLong("version"));}
    public List<UniversityResponse> universities(String query,String city,String type,int page,int size){return jdbc.query("SELECT "+UNIVERSITY_COLUMNS+" FROM universities u WHERE u.deleted_at IS NULL AND strpos(search_fold(u.name),search_fold(?))>0 AND (?='' OR search_fold(coalesce(u.city,''))=search_fold(?)) AND (?='' OR u.institution_type=?) ORDER BY u.normalized_name,u.id LIMIT ? OFFSET ?",this::university,query,city,city,type,type,size,page*size);}
    public long universityCount(String query,String city,String type){return jdbc.queryForObject("SELECT count(*) FROM universities WHERE deleted_at IS NULL AND strpos(search_fold(name),search_fold(?))>0 AND (?='' OR search_fold(coalesce(city,''))=search_fold(?)) AND (?='' OR institution_type=?)",Long.class,query,city,city,type,type);}
    public Optional<UniversityResponse> university(UUID id,boolean lock){return jdbc.query("SELECT "+UNIVERSITY_COLUMNS+" FROM universities u WHERE u.id=?"+(lock?" FOR UPDATE OF u":""),this::university,id).stream().findFirst();}
    public void universityDetails(UUID id,UniversityDetailsRequest r){jdbc.update("UPDATE universities SET city=nullif(trim(?),''),institution_type=coalesce(?,institution_type),description=nullif(trim(?),''),website_url=nullif(trim(?),''),logo_url=nullif(trim(?),''),accent_primary=nullif(upper(trim(?)),''),accent_soft=nullif(upper(trim(?)),''),accent_foreground=nullif(upper(trim(?)),''),updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",r.city(),r.institutionType(),r.description(),r.websiteUrl(),r.logoUrl(),r.accentPrimary(),r.accentSoft(),r.accentForeground(),id);}
    public List<CatalogEntry> list(CatalogKind kind,String query,boolean includeDeleted,int page,int size) {
        return jdbc.query("SELECT id,name,deleted_at,version FROM "+kind.table()+" WHERE (? OR deleted_at IS NULL) AND strpos(search_fold(name),search_fold(?))>0 ORDER BY normalized_name,id LIMIT ? OFFSET ?",
                this::entry,includeDeleted,query,size,page*size);
    }
    public long count(CatalogKind kind,String query,boolean includeDeleted) {
        return jdbc.queryForObject("SELECT count(*) FROM "+kind.table()+" WHERE (? OR deleted_at IS NULL) AND strpos(search_fold(name),search_fold(?))>0",Long.class,includeDeleted,query);
    }
    public Optional<CatalogEntry> lock(CatalogKind kind,UUID id) {
        return jdbc.query("SELECT id,name,deleted_at,version FROM "+kind.table()+" WHERE id=? FOR UPDATE",this::entry,id).stream().findFirst();
    }
    public Optional<CatalogEntry> find(CatalogKind kind,UUID id) {
        return jdbc.query("SELECT id,name,deleted_at,version FROM "+kind.table()+" WHERE id=?",this::entry,id).stream().findFirst();
    }
    public Optional<CatalogEntry> byNormalizedName(CatalogKind kind,String normalized) {
        return jdbc.query("SELECT id,name,deleted_at,version FROM "+kind.table()+" WHERE normalized_name=? FOR UPDATE",this::entry,normalized).stream().findFirst();
    }
    public void create(CatalogKind kind,UUID id,String name,String normalized,UUID actor) {
        if(kind==CatalogKind.TAG) jdbc.update("INSERT INTO tags(id,name,normalized_name,created_by) VALUES (?,?,?,?)",id,name,normalized,actor);
        else jdbc.update("INSERT INTO "+kind.table()+"(id,name,normalized_name) VALUES (?,?,?)",id,name,normalized);
    }
    public void rename(CatalogKind kind,UUID id,String name,String normalized) {
        jdbc.update("UPDATE "+kind.table()+" SET name=?,normalized_name=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",name,normalized,id);
    }
    public void status(CatalogKind kind,UUID id,boolean deleted) {
        jdbc.update("UPDATE "+kind.table()+" SET deleted_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",deleted,id);
    }
    public boolean universityInUse(UUID id) {
        return jdbc.queryForObject("""
                SELECT EXISTS (
                  SELECT 1 FROM user_profiles WHERE university_id=? OR university_department_id IN (SELECT id FROM university_departments WHERE university_id=?)
                  UNION ALL SELECT 1 FROM questions WHERE university_id=? OR university_department_id IN (SELECT id FROM university_departments WHERE university_id=?)
                  UNION ALL SELECT 1 FROM admin_applications WHERE university_id=? OR university_department_id IN (SELECT id FROM university_departments WHERE university_id=?)
                )
                """,Boolean.class,id,id,id,id,id,id);
    }
    public void deleteUniversity(UUID id) {
        jdbc.update("DELETE FROM university_departments WHERE university_id=?",id);
        jdbc.update("DELETE FROM universities WHERE id=?",id);
    }
    private static final String EDUCATION = "SELECT ud.id,ud.university_id,u.name university_name,ud.department_id,d.name department_name,ud.deleted_at,ud.version,(ud.deleted_at IS NULL AND u.deleted_at IS NULL AND d.deleted_at IS NULL) available FROM university_departments ud JOIN universities u ON u.id=ud.university_id JOIN departments d ON d.id=ud.department_id ";
    private EducationResponse mapEducation(ResultSet rs,int n) throws SQLException {
        return new EducationResponse(rs.getObject("id",UUID.class),rs.getObject("university_id",UUID.class),rs.getString("university_name"),
                rs.getObject("department_id",UUID.class),rs.getString("department_name"),rs.getTimestamp("deleted_at")==null?null:rs.getTimestamp("deleted_at").toInstant(),rs.getBoolean("available"),rs.getLong("version"));
    }
    public Optional<EducationResponse> education(UUID id) { return jdbc.query(EDUCATION+"WHERE ud.id=?",this::mapEducation,id).stream().findFirst(); }
    public void lockEducationRow(UUID id) { jdbc.queryForObject("SELECT id FROM university_departments WHERE id=? FOR UPDATE",UUID.class,id); }
    public List<EducationResponse> educationList(UUID university,String query,boolean includeDeleted,int page,int size) {
        return jdbc.query(EDUCATION+"WHERE ud.university_id=? AND (? OR (ud.deleted_at IS NULL AND u.deleted_at IS NULL AND d.deleted_at IS NULL)) AND strpos(search_fold(d.name),search_fold(?))>0 ORDER BY d.normalized_name,ud.id LIMIT ? OFFSET ?",
                this::mapEducation,university,includeDeleted,query,size,page*size);
    }
    public long educationCount(UUID university,String query,boolean includeDeleted) {
        return jdbc.queryForObject("SELECT count(*) FROM university_departments ud JOIN universities u ON u.id=ud.university_id JOIN departments d ON d.id=ud.department_id WHERE ud.university_id=? AND (? OR (ud.deleted_at IS NULL AND u.deleted_at IS NULL AND d.deleted_at IS NULL)) AND strpos(search_fold(d.name),search_fold(?))>0",Long.class,university,includeDeleted,query);
    }
    public List<EducationResponse> publicProgramList(String query,int page,int size) {
        return jdbc.query(EDUCATION+"WHERE ud.deleted_at IS NULL AND u.deleted_at IS NULL AND d.deleted_at IS NULL AND (strpos(search_fold(d.name),search_fold(?))>0 OR strpos(search_fold(u.name),search_fold(?))>0) ORDER BY d.normalized_name,u.normalized_name,ud.id LIMIT ? OFFSET ?",
                this::mapEducation,query,query,size,page*size);
    }
    public long publicProgramCount(String query) {
        return jdbc.queryForObject("SELECT count(*) FROM university_departments ud JOIN universities u ON u.id=ud.university_id JOIN departments d ON d.id=ud.department_id WHERE ud.deleted_at IS NULL AND u.deleted_at IS NULL AND d.deleted_at IS NULL AND (strpos(search_fold(d.name),search_fold(?))>0 OR strpos(search_fold(u.name),search_fold(?))>0)",Long.class,query,query);
    }
    public void createEducation(UUID id,UUID university,UUID department) {
        jdbc.update("INSERT INTO university_departments(id,university_id,department_id) VALUES (?,?,?)",id,university,department);
    }
    public Optional<EducationResponse> education(UUID university,UUID department) {
        return jdbc.query(EDUCATION+"WHERE ud.university_id=? AND ud.department_id=? FOR UPDATE OF ud",this::mapEducation,university,department).stream().findFirst();
    }
    public Optional<EducationResponse> findEducation(UUID university,UUID department) {
        return jdbc.query(EDUCATION+"WHERE ud.university_id=? AND ud.department_id=?",this::mapEducation,university,department).stream().findFirst();
    }
    public void educationStatus(UUID id,boolean deleted) {
        jdbc.update("UPDATE university_departments SET deleted_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",deleted,id);
    }
    public void audit(UUID actor,String action,String type,UUID id,String reason) {
        jdbc.update("INSERT INTO management_actions(id,actor_id,action,target_type,target_id,reason) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),actor,action,type,id,reason);
    }
}
