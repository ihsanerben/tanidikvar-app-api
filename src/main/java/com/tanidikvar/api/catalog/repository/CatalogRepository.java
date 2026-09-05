package com.tanidikvar.api.catalog.repository;

import com.tanidikvar.api.catalog.entity.*;
import com.tanidikvar.api.catalog.dto.EducationResponse;
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
    public void createEducation(UUID id,UUID university,UUID department) {
        jdbc.update("INSERT INTO university_departments(id,university_id,department_id) VALUES (?,?,?)",id,university,department);
    }
    public void educationStatus(UUID id,boolean deleted) {
        jdbc.update("UPDATE university_departments SET deleted_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",deleted,id);
    }
    public void audit(UUID actor,String action,String type,UUID id) {
        jdbc.update("INSERT INTO management_actions(id,actor_id,action,target_type,target_id) VALUES (?,?,?,?,?)",UUID.randomUUID(),actor,action,type,id);
    }
}
