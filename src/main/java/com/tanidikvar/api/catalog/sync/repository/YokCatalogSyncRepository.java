package com.tanidikvar.api.catalog.sync.repository;

import com.tanidikvar.api.catalog.service.CatalogNames;
import com.tanidikvar.api.catalog.sync.dto.YokCatalogSyncResponse;
import com.tanidikvar.api.catalog.sync.dto.YokCatalogQualityReport;
import com.tanidikvar.api.catalog.sync.model.YokAtlasProgram;
import com.tanidikvar.api.catalog.sync.model.YokAtlasSnapshot;
import com.tanidikvar.api.catalog.sync.model.YokAtlasYearStats;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class YokCatalogSyncRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public YokCatalogSyncRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }

    public void start(UUID id,UUID actor,String operation) {
        jdbc.update("""
                INSERT INTO catalog_sync_runs(id,source,status,snapshot_checksum,created_by,operation)
                VALUES (?,'TURKIYE_PROGRAMS','STARTED',?,?,?)
                """,id,"0".repeat(64),actor,operation);
    }

    public Optional<YokCatalogSyncResponse> find(UUID id) {
        return jdbc.query("""
                SELECT id,operation,status,snapshot_checksum,universities_seen,programs_seen,options_seen,
                       started_at,completed_at,failure_reason,quality_report::text AS quality_report
                FROM catalog_sync_runs WHERE id=?
                """,this::run,id).stream().findFirst();
    }
    public List<YokCatalogSyncResponse> history(int size){return jdbc.query("SELECT id,operation,status,snapshot_checksum,universities_seen,programs_seen,options_seen,started_at,completed_at,failure_reason,quality_report::text AS quality_report FROM catalog_sync_runs WHERE source='TURKIYE_PROGRAMS' ORDER BY started_at DESC,id DESC LIMIT ?",this::run,size);}

    public String lockStatus(UUID id) {
        return jdbc.queryForObject("SELECT status FROM catalog_sync_runs WHERE id=? FOR UPDATE",String.class,id);
    }

    public boolean successfulSnapshotExists(String checksum) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM catalog_sync_runs
                              WHERE source='TURKIYE_PROGRAMS' AND operation='APPLY'
                                AND snapshot_checksum=? AND status='SUCCEEDED')
                """,Boolean.class,checksum));
    }

    public YokCatalogQualityReport preview(UUID id,YokAtlasSnapshot snapshot) {
        Set<Long> existingSourceIds=new HashSet<>();
        Set<String> manualNames=new HashSet<>();
        jdbc.query("SELECT source_university_id,normalized_name,catalog_source FROM universities WHERE deleted_at IS NULL",rs->{
            Long sourceId=rs.getObject("source_university_id",Long.class);
            String catalogSource=rs.getString("catalog_source");
            if(sourceId!=null&&"TURKIYE_PROGRAMS".equals(catalogSource))existingSourceIds.add(sourceId);
            if(!"YOK_ATLAS".equals(catalogSource)&&!"TURKIYE_PROGRAMS".equals(catalogSource))manualNames.add(rs.getString("normalized_name"));
        });
        Set<Long> universities=new HashSet<>(),families=new HashSet<>();
        Set<String> programs=new HashSet<>(),cities=new HashSet<>();
        int state=0,foundation=0,undergraduate=0,associate=0,withoutUnit=0,withoutRank=0;
        Set<Long> stateIds=new HashSet<>(),foundationIds=new HashSet<>();
        Set<String> collisionNames=new HashSet<>();
        for(YokAtlasProgram row:snapshot.programs()) {
            universities.add(row.universityId());families.add(row.programGroupId());
            programs.add(row.universityId()+":"+row.programGroupId());
            if(row.universityCity()!=null&&!row.universityCity().isBlank())cities.add(CatalogNames.normalized(row.universityCity()));
            if("DEVLET".equals(row.universityType()))stateIds.add(row.universityId());
            else if("VAKIF".equals(row.universityType()))foundationIds.add(row.universityId());
            if("LISANS".equals(row.degreeLevel()))undergraduate++;else associate++;
            if(row.academicUnitId()==null)withoutUnit++;
            if(row.statistics().isEmpty()||row.statistics().getFirst().successRank()==null)withoutRank++;
            if(manualNames.contains(CatalogNames.normalized(row.universityName())))collisionNames.add(CatalogNames.normalized(row.universityName()));
        }
        state=stateIds.size();foundation=foundationIds.size();
        int existing=(int)universities.stream().filter(existingSourceIds::contains).count();
        var report=new YokCatalogQualityReport(universities.size(),families.size(),programs.size(),snapshot.programs().size(),
                cities.size(),state,foundation,undergraduate,associate,withoutUnit,withoutRank,existing,
                universities.size()-existing,collisionNames.size());
        jdbc.update("""
                UPDATE catalog_sync_runs SET status='SUCCEEDED',snapshot_checksum=?,source_updated_at=?,
                    universities_seen=?,programs_seen=?,options_seen=?,quality_report=?::jsonb,
                    completed_at=CURRENT_TIMESTAMP WHERE id=?
                """,snapshot.checksum(),snapshot.sourceUpdatedAt(),report.universityCount(),report.programCount(),
                report.optionCount(),json.writeValueAsString(report),id);
        return report;
    }

    public void skip(UUID id,YokAtlasSnapshot snapshot,int universities) {
        jdbc.update("""
                UPDATE catalog_sync_runs SET status='SKIPPED',snapshot_checksum=?,source_updated_at=?,
                    universities_seen=?,programs_seen=?,options_seen=?,completed_at=CURRENT_TIMESTAMP
                WHERE id=?
                """,snapshot.checksum(),snapshot.sourceUpdatedAt(),universities,
                distinctPrograms(snapshot),snapshot.programs().size(),id);
    }

    public void fail(UUID id,String reason) {
        jdbc.update("""
                UPDATE catalog_sync_runs SET status='FAILED',failure_reason=?,completed_at=CURRENT_TIMESTAMP
                WHERE id=? AND status='STARTED'
                """,reason,id);
    }

    public void apply(UUID runId,YokAtlasSnapshot snapshot) {
        Instant startedAt=jdbc.queryForObject("SELECT started_at FROM catalog_sync_runs WHERE id=?",Instant.class,runId);
        Timestamp cutoff=Timestamp.from(startedAt);
        for(YokAtlasProgram row:snapshot.programs()) upsert(row);
        deactivateMissing(cutoff);
        int universities=jdbc.queryForObject("""
                SELECT count(DISTINCT source_university_id) FROM universities
                WHERE catalog_source='TURKIYE_PROGRAMS' AND updated_at>?
                """,Integer.class,cutoff);
        jdbc.update("""
                UPDATE catalog_sync_runs SET status='SUCCEEDED',snapshot_checksum=?,source_updated_at=?,
                    universities_seen=?,programs_seen=?,options_seen=?,completed_at=CURRENT_TIMESTAMP
                WHERE id=?
                """,snapshot.checksum(),snapshot.sourceUpdatedAt(),universities,
                distinctPrograms(snapshot),snapshot.programs().size(),runId);
    }

    private void upsert(YokAtlasProgram row) {
        String universityName=CatalogNames.clean(row.universityName());
        String universityNormalized=CatalogNames.normalized(universityName);
        UUID university=university(row,universityName,universityNormalized);
        String familyName=CatalogNames.clean(row.programGroupName());
        String familyNormalized=CatalogNames.normalized(familyName);
        UUID family=jdbc.queryForObject("""
                INSERT INTO program_families(id,source,source_program_group_id,name,normalized_name,degree_level)
                VALUES (?,'TURKIYE_PROGRAMS',?,?,?,?)
                ON CONFLICT (source,source_program_group_id,degree_level) DO UPDATE SET
                    name=excluded.name,normalized_name=excluded.normalized_name,deleted_at=NULL,
                    updated_at=CURRENT_TIMESTAMP,version=program_families.version+1
                RETURNING id
                """,UUID.class,UUID.randomUUID(),row.programGroupId(),familyName,familyNormalized,row.degreeLevel());
        UUID unit=academicUnit(row,university);
        UUID program=jdbc.queryForObject("""
                INSERT INTO programs(id,university_id,program_family_id,display_name,normalized_name)
                VALUES (?,?,?,?,?)
                ON CONFLICT (university_id,program_family_id) DO UPDATE SET
                    display_name=excluded.display_name,normalized_name=excluded.normalized_name,deleted_at=NULL,
                    updated_at=CURRENT_TIMESTAMP,version=programs.version+1
                RETURNING id
                """,UUID.class,UUID.randomUUID(),university,family,familyName,familyNormalized);
        UUID option=jdbc.queryForObject("""
                INSERT INTO admission_options(id,program_id,academic_unit_id,source_program_id,osym_guide_id,
                    guide_code,score_type,education_type,language,scholarship,duration_years)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (guide_code) DO UPDATE SET
                    program_id=excluded.program_id,academic_unit_id=excluded.academic_unit_id,
                    source_program_id=excluded.source_program_id,osym_guide_id=excluded.osym_guide_id,
                    score_type=excluded.score_type,education_type=excluded.education_type,
                    language=excluded.language,scholarship=excluded.scholarship,duration_years=excluded.duration_years,
                    deleted_at=NULL,updated_at=CURRENT_TIMESTAMP,version=admission_options.version+1
                RETURNING id
                """,UUID.class,UUID.randomUUID(),program,unit,row.sourceProgramId(),row.osymGuideId(),row.guideCode(),
                row.scoreType(),row.educationType(),row.language(),row.scholarship(),row.durationYears());
        for(YokAtlasYearStats stats:row.statistics()) {
            jdbc.update("""
                    INSERT INTO admission_statistics(admission_option_id,guide_year,quota,placed,minimum_score,
                        success_rank,maximum_score,placed_male,placed_female,average_secondary_score,total_preferences,
                        demand_per_quota,average_preference_rank,quota_general,quota_school_first,quota_martyr_veteran,
                        quota_woman_34plus,quota_earthquake,statistics_source,source_payload_checksum)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT (admission_option_id,guide_year) DO UPDATE SET
                        quota=excluded.quota,placed=excluded.placed,minimum_score=excluded.minimum_score,
                        success_rank=excluded.success_rank,maximum_score=excluded.maximum_score,placed_male=excluded.placed_male,
                        placed_female=excluded.placed_female,average_secondary_score=excluded.average_secondary_score,
                        total_preferences=excluded.total_preferences,demand_per_quota=excluded.demand_per_quota,
                        average_preference_rank=excluded.average_preference_rank,quota_general=excluded.quota_general,
                        quota_school_first=excluded.quota_school_first,quota_martyr_veteran=excluded.quota_martyr_veteran,
                        quota_woman_34plus=excluded.quota_woman_34plus,quota_earthquake=excluded.quota_earthquake,
                        statistics_source=excluded.statistics_source,source_payload_checksum=excluded.source_payload_checksum,
                        updated_at=CURRENT_TIMESTAMP
                    """,option,stats.year(),stats.quota(),stats.placed(),stats.minimumScore(),stats.successRank(),
                    stats.maximumScore(),stats.placedMale(),stats.placedFemale(),stats.averageSecondaryScore(),
                    stats.totalPreferences(),stats.demandPerQuota(),stats.averagePreferenceRank(),stats.quotaGeneral(),
                    stats.quotaSchoolFirst(),stats.quotaMartyrVeteran(),stats.quotaWoman34Plus(),stats.quotaEarthquake(),
                    stats.statisticsSource(),stats.payloadChecksum());
        }
        UUID department=department(familyName,familyNormalized);
        jdbc.update("""
                INSERT INTO university_departments(id,university_id,department_id,program_id)
                VALUES (?,?,?,?)
                ON CONFLICT (university_id,department_id) DO UPDATE SET
                    program_id=excluded.program_id,deleted_at=NULL,updated_at=CURRENT_TIMESTAMP,
                    version=university_departments.version+1
                """,UUID.randomUUID(),university,department,program);
    }

    private UUID university(YokAtlasProgram row,String name,String normalized) {
        Optional<UUID> sourced=jdbc.query("""
                SELECT id FROM universities WHERE catalog_source='TURKIYE_PROGRAMS' AND source_university_id=? FOR UPDATE
                """,(r,n)->r.getObject(1,UUID.class),row.universityId()).stream().findFirst();
        if(sourced.isPresent()) {
            jdbc.update("""
                    UPDATE universities SET name=?,normalized_name=?,city=?,institution_type=?,deleted_at=NULL,
                        updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?
                    """,name,normalized,row.universityCity(),row.universityType(),sourced.get());
            return sourced.get();
        }
        return jdbc.queryForObject("""
                INSERT INTO universities(id,name,normalized_name,city,institution_type,catalog_source,source_university_id)
                VALUES (?,?,?,?,?,'TURKIYE_PROGRAMS',?)
                ON CONFLICT (normalized_name) DO UPDATE SET
                    name=excluded.name,city=excluded.city,institution_type=excluded.institution_type,
                    catalog_source='TURKIYE_PROGRAMS',source_university_id=excluded.source_university_id,
                    deleted_at=NULL,updated_at=CURRENT_TIMESTAMP,version=universities.version+1
                RETURNING id
                """,UUID.class,UUID.randomUUID(),name,normalized,row.universityCity(),row.universityType(),row.universityId());
    }

    private UUID academicUnit(YokAtlasProgram row,UUID university) {
        if(row.academicUnitId()==null||row.academicUnitName()==null||row.academicUnitName().isBlank()) return null;
        return jdbc.queryForObject("""
                INSERT INTO academic_units(id,university_id,source,source_unit_id,name,unit_type,city,district)
                VALUES (?,?,'TURKIYE_PROGRAMS',?,?,?,?,?)
                ON CONFLICT (university_id,source,source_unit_id) DO UPDATE SET
                    name=excluded.name,unit_type=excluded.unit_type,city=excluded.city,district=excluded.district,
                    deleted_at=NULL,updated_at=CURRENT_TIMESTAMP,version=academic_units.version+1
                RETURNING id
                """,UUID.class,UUID.randomUUID(),university,row.academicUnitId(),row.academicUnitName(),
                row.academicUnitType(),row.city(),row.district());
    }

    private UUID department(String name,String normalized) {
        Optional<UUID> existing=jdbc.query("SELECT id FROM departments WHERE normalized_name=? FOR UPDATE",
                (r,n)->r.getObject(1,UUID.class),normalized).stream().findFirst();
        if(existing.isPresent()) {
            jdbc.update("UPDATE departments SET deleted_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",existing.get());
            return existing.get();
        }
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO departments(id,name,normalized_name) VALUES (?,?,?)",id,name,normalized);
        return id;
    }

    private void deactivateMissing(Timestamp cutoff) {
        jdbc.update("""
                UPDATE admission_options option SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=option.version+1
                WHERE option.deleted_at IS NULL AND option.updated_at<=? AND EXISTS (
                    SELECT 1 FROM programs program JOIN program_families family ON family.id=program.program_family_id
                    WHERE program.id=option.program_id AND family.source IN ('YOK_ATLAS','TURKIYE_PROGRAMS'))
                """,cutoff);
        jdbc.update("""
                UPDATE programs program SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=program.version+1
                WHERE program.deleted_at IS NULL AND program.updated_at<=? AND EXISTS (
                    SELECT 1 FROM program_families family
                    WHERE family.id=program.program_family_id AND family.source IN ('YOK_ATLAS','TURKIYE_PROGRAMS'))
                """,cutoff);
        jdbc.update("UPDATE academic_units SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE source IN ('YOK_ATLAS','TURKIYE_PROGRAMS') AND deleted_at IS NULL AND updated_at<=?",cutoff);
        jdbc.update("UPDATE program_families SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE source IN ('YOK_ATLAS','TURKIYE_PROGRAMS') AND deleted_at IS NULL AND updated_at<=?",cutoff);
        jdbc.update("UPDATE university_departments SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE deleted_at IS NULL AND updated_at<=?",cutoff);
        jdbc.update("UPDATE departments SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE deleted_at IS NULL AND updated_at<=?",cutoff);
        jdbc.update("UPDATE universities SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE deleted_at IS NULL AND updated_at<=?",cutoff);
    }

    private static int distinctPrograms(YokAtlasSnapshot snapshot) {
        return (int)snapshot.programs().stream().map(row->row.universityId()+":"+row.programGroupId()).distinct().count();
    }

    private YokCatalogSyncResponse run(ResultSet rs,int ignored)throws SQLException {
        String reportJson=rs.getString("quality_report");
        YokCatalogQualityReport report=reportJson==null?null:json.readValue(reportJson,YokCatalogQualityReport.class);
        return new YokCatalogSyncResponse(rs.getObject("id",UUID.class),rs.getString("operation"),rs.getString("status"),
                rs.getString("snapshot_checksum"),rs.getInt("universities_seen"),rs.getInt("programs_seen"),
                rs.getInt("options_seen"),rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("completed_at")==null?null:rs.getTimestamp("completed_at").toInstant(),
                rs.getString("failure_reason"),report);
    }
}
