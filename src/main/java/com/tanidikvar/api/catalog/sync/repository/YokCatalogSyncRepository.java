package com.tanidikvar.api.catalog.sync.repository;

import com.tanidikvar.api.catalog.service.CatalogNames;
import com.tanidikvar.api.catalog.sync.dto.YokCatalogSyncResponse;
import com.tanidikvar.api.catalog.sync.dto.YokCatalogQualityReport;
import com.tanidikvar.api.catalog.sync.model.YokAtlasProgram;
import com.tanidikvar.api.catalog.sync.model.YokAtlasNetStats;
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
                VALUES (?,'YOK_ATLAS','STARTED',?,?,?)
                """,id,"0".repeat(64),actor,operation);
    }

    public Optional<YokCatalogSyncResponse> find(UUID id) {
        return jdbc.query("""
                SELECT id,operation,status,snapshot_checksum,universities_seen,programs_seen,options_seen,
                       started_at,completed_at,failure_reason,quality_report::text AS quality_report
                FROM catalog_sync_runs WHERE id=?
                """,this::run,id).stream().findFirst();
    }
    public List<YokCatalogSyncResponse> history(int size){return jdbc.query("SELECT id,operation,status,snapshot_checksum,universities_seen,programs_seen,options_seen,started_at,completed_at,failure_reason,quality_report::text AS quality_report FROM catalog_sync_runs WHERE source='YOK_ATLAS' ORDER BY started_at DESC,id DESC LIMIT ?",this::run,size);}

    public String lockStatus(UUID id) {
        return jdbc.queryForObject("SELECT status FROM catalog_sync_runs WHERE id=? FOR UPDATE",String.class,id);
    }

    public boolean successfulSnapshotExists(String checksum) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM catalog_sync_runs
                              WHERE source='YOK_ATLAS' AND operation='APPLY'
                                AND snapshot_checksum=? AND status='SUCCEEDED')
                """,Boolean.class,checksum));
    }

    public YokCatalogQualityReport preview(UUID id,YokAtlasSnapshot snapshot) {
        Set<String> existingNames=new HashSet<>();
        Set<String> manualNames=new HashSet<>();
        jdbc.query("SELECT source_university_id,normalized_name,catalog_source FROM universities WHERE deleted_at IS NULL",rs->{
            String catalogSource=rs.getString("catalog_source");
            if("YOK_ATLAS".equals(catalogSource)||"TURKIYE_PROGRAMS".equals(catalogSource))existingNames.add(rs.getString("normalized_name"));
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
        int existing=(int)snapshot.programs().stream().map(row->CatalogNames.normalized(row.universityName())).distinct().filter(existingNames::contains).count();
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
        upsertNetStatistics(snapshot.netStatistics());
        deactivateMissing(cutoff);
        int universities=jdbc.queryForObject("""
                SELECT count(DISTINCT source_university_id) FROM universities
                WHERE catalog_source='YOK_ATLAS' AND updated_at>?
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
        Optional<UUID> matchedFamily=jdbc.query("""
                SELECT id FROM program_families
                WHERE (source='YOK_ATLAS' AND source_program_group_id=? AND degree_level=?)
                   OR (source IN ('YOK_ATLAS','TURKIYE_PROGRAMS') AND normalized_name=? AND degree_level=?)
                ORDER BY CASE WHEN source='YOK_ATLAS' AND source_program_group_id=? THEN 0 ELSE 1 END LIMIT 1 FOR UPDATE
                """,(r,n)->r.getObject(1,UUID.class),row.programGroupId(),row.degreeLevel(),familyNormalized,row.degreeLevel(),row.programGroupId()).stream().findFirst();
        UUID family=matchedFamily.orElse(null);
        if(family==null){family=UUID.randomUUID();jdbc.update("INSERT INTO program_families(id,source,source_program_group_id,name,normalized_name,degree_level) VALUES (?,'YOK_ATLAS',?,?,?,?)",family,row.programGroupId(),familyName,familyNormalized,row.degreeLevel());}
        else jdbc.update("UPDATE program_families SET source='YOK_ATLAS',source_program_group_id=?,name=?,normalized_name=?,deleted_at=NULL,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",row.programGroupId(),familyName,familyNormalized,family);
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
                    guide_code,score_type,education_type,language,scholarship,duration_years,source_payload)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?::jsonb)
                ON CONFLICT (guide_code) DO UPDATE SET
                    program_id=excluded.program_id,academic_unit_id=excluded.academic_unit_id,
                    source_program_id=excluded.source_program_id,osym_guide_id=excluded.osym_guide_id,
                    score_type=excluded.score_type,education_type=excluded.education_type,
                    language=excluded.language,scholarship=excluded.scholarship,duration_years=excluded.duration_years,
                    source_payload=excluded.source_payload,
                    deleted_at=NULL,updated_at=CURRENT_TIMESTAMP,version=admission_options.version+1
                RETURNING id
                """,UUID.class,UUID.randomUUID(),program,unit,row.sourceProgramId(),row.osymGuideId(),row.guideCode(),
                row.scoreType(),row.educationType(),row.language(),row.scholarship(),row.durationYears(),row.sourcePayload());
        jdbc.update("""
                UPDATE admission_options SET
                    annual_fee=NULLIF(source_payload->>'ucret','')::numeric,
                    special_quota_type=NULLIF(source_payload->>'birimEkTuru','')
                WHERE id=?
                """,option);
        if(row.sourcePayload()!=null)jdbc.update("""
                INSERT INTO program_academic_details(id,program_id,academic_unit_id,professor_count,
                    associate_professor_count,doctor_faculty_member_count,research_assistant_count,
                    accreditation_code,accreditation_description,minimum_success_rank,tyc_qualified)
                SELECT ?,?,?,NULLIF(payload->>'prof','')::integer,NULLIF(payload->>'doc','')::integer,
                    NULLIF(payload->>'dou','')::integer,NULLIF(payload->>'arGor','')::integer,
                    NULLIF(payload->>'akreditasyon',''),NULLIF(payload->>'akreditasyonAck',''),
                    NULLIF(payload->>'minBasariSirasi','')::integer,
                    CASE WHEN jsonb_exists(payload,'tyc') THEN payload->>'tyc'='*' END
                FROM (SELECT ?::jsonb payload) source
                ON CONFLICT (program_id,academic_unit_id) DO UPDATE SET
                    professor_count=COALESCE(excluded.professor_count,program_academic_details.professor_count),
                    associate_professor_count=COALESCE(excluded.associate_professor_count,program_academic_details.associate_professor_count),
                    doctor_faculty_member_count=COALESCE(excluded.doctor_faculty_member_count,program_academic_details.doctor_faculty_member_count),
                    research_assistant_count=COALESCE(excluded.research_assistant_count,program_academic_details.research_assistant_count),
                    accreditation_code=COALESCE(excluded.accreditation_code,program_academic_details.accreditation_code),
                    accreditation_description=COALESCE(excluded.accreditation_description,program_academic_details.accreditation_description),
                    minimum_success_rank=COALESCE(excluded.minimum_success_rank,program_academic_details.minimum_success_rank),
                    tyc_qualified=COALESCE(excluded.tyc_qualified,program_academic_details.tyc_qualified),
                    updated_at=CURRENT_TIMESTAMP
                """,UUID.randomUUID(),program,unit,row.sourcePayload());
        for(YokAtlasYearStats stats:row.statistics()) {
            jdbc.update("""
                    INSERT INTO admission_statistics(admission_option_id,guide_year,quota,placed,minimum_score,
                        success_rank,maximum_score,placed_male,placed_female,average_secondary_score,total_preferences,
                        demand_per_quota,average_preference_rank,quota_general,quota_school_first,quota_martyr_veteran,
                        quota_woman_34plus,quota_earthquake,statistics_source,source_payload_checksum)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT (admission_option_id,guide_year) DO UPDATE SET
                        quota=COALESCE(excluded.quota,admission_statistics.quota),placed=COALESCE(excluded.placed,admission_statistics.placed),
                        minimum_score=COALESCE(excluded.minimum_score,NULLIF(admission_statistics.minimum_score,0)),success_rank=COALESCE(excluded.success_rank,admission_statistics.success_rank),
                        maximum_score=COALESCE(excluded.maximum_score,admission_statistics.maximum_score),placed_male=COALESCE(excluded.placed_male,admission_statistics.placed_male),
                        placed_female=COALESCE(excluded.placed_female,admission_statistics.placed_female),average_secondary_score=COALESCE(excluded.average_secondary_score,admission_statistics.average_secondary_score),
                        total_preferences=COALESCE(excluded.total_preferences,admission_statistics.total_preferences),demand_per_quota=COALESCE(excluded.demand_per_quota,admission_statistics.demand_per_quota),
                        average_preference_rank=COALESCE(excluded.average_preference_rank,admission_statistics.average_preference_rank),quota_general=COALESCE(excluded.quota_general,admission_statistics.quota_general),
                        quota_school_first=COALESCE(excluded.quota_school_first,admission_statistics.quota_school_first),quota_martyr_veteran=COALESCE(excluded.quota_martyr_veteran,admission_statistics.quota_martyr_veteran),
                        quota_woman_34plus=COALESCE(excluded.quota_woman_34plus,admission_statistics.quota_woman_34plus),quota_earthquake=COALESCE(excluded.quota_earthquake,admission_statistics.quota_earthquake),
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
                SELECT id FROM universities WHERE (catalog_source='YOK_ATLAS' AND source_university_id=?) OR normalized_name=?
                ORDER BY CASE WHEN catalog_source='YOK_ATLAS' AND source_university_id=? THEN 0 ELSE 1 END LIMIT 1 FOR UPDATE
                """,(r,n)->r.getObject(1,UUID.class),row.universityId(),normalized,row.universityId()).stream().findFirst();
        if(sourced.isPresent()) {
            jdbc.update("""
                    UPDATE universities SET name=?,normalized_name=?,city=?,institution_type=?,catalog_source='YOK_ATLAS',source_university_id=?,deleted_at=NULL,
                        updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?
                    """,name,normalized,row.universityCity(),row.universityType(),row.universityId(),sourced.get());
            return sourced.get();
        }
        return jdbc.queryForObject("""
                INSERT INTO universities(id,name,normalized_name,city,institution_type,catalog_source,source_university_id)
                VALUES (?,?,?,?,?,'YOK_ATLAS',?)
                ON CONFLICT (normalized_name) DO UPDATE SET
                    name=excluded.name,city=excluded.city,institution_type=excluded.institution_type,
                    catalog_source='YOK_ATLAS',source_university_id=excluded.source_university_id,
                    deleted_at=NULL,updated_at=CURRENT_TIMESTAMP,version=universities.version+1
                RETURNING id
                """,UUID.class,UUID.randomUUID(),name,normalized,row.universityCity(),row.universityType(),row.universityId());
    }

    private UUID academicUnit(YokAtlasProgram row,UUID university) {
        if(row.academicUnitId()==null||row.academicUnitName()==null||row.academicUnitName().isBlank()) return null;
        Optional<UUID> existing=jdbc.query("""
                SELECT id FROM academic_units
                WHERE university_id=? AND (
                    (source='YOK_ATLAS' AND source_unit_id=?)
                    OR (source IN ('YOK_ATLAS','TURKIYE_PROGRAMS') AND search_fold(name)=search_fold(?)))
                ORDER BY CASE WHEN source='YOK_ATLAS' AND source_unit_id=? THEN 0 ELSE 1 END
                LIMIT 1 FOR UPDATE
                """,(r,n)->r.getObject(1,UUID.class),university,row.academicUnitId(),row.academicUnitName(),row.academicUnitId())
                .stream().findFirst();
        if(existing.isPresent()) {
            jdbc.update("""
                    UPDATE academic_units SET source='YOK_ATLAS',source_unit_id=?,name=?,unit_type=?,city=?,district=?,
                        deleted_at=NULL,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?
                    """,row.academicUnitId(),row.academicUnitName(),row.academicUnitType(),row.city(),row.district(),existing.get());
            return existing.get();
        }
        return jdbc.queryForObject("""
                INSERT INTO academic_units(id,university_id,source,source_unit_id,name,unit_type,city,district)
                VALUES (?,?,'YOK_ATLAS',?,?,?,?,?)
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

    private void upsertNetStatistics(List<YokAtlasNetStats> rows) {
        if(rows.isEmpty()) return;
        String sourceSql="""
                INSERT INTO yok_atlas_net_statistics(guide_code,guide_year,minimum_score,average_secondary_score,
                    score_coefficient,tyt_turkish_net,tyt_social_net,tyt_math_net,tyt_science_net,ayt_math_net,
                    ayt_physics_net,ayt_chemistry_net,ayt_biology_net,ayt_literature_net,ayt_history1_net,
                    ayt_geography1_net,ayt_history2_net,ayt_geography2_net,ayt_philosophy_net,ayt_religion_net,
                    foreign_language_net,source_payload_checksum,source_payload)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)
                ON CONFLICT (guide_code,guide_year) DO UPDATE SET
                    minimum_score=excluded.minimum_score,average_secondary_score=excluded.average_secondary_score,
                    score_coefficient=excluded.score_coefficient,tyt_turkish_net=excluded.tyt_turkish_net,
                    tyt_social_net=excluded.tyt_social_net,tyt_math_net=excluded.tyt_math_net,
                    tyt_science_net=excluded.tyt_science_net,ayt_math_net=excluded.ayt_math_net,
                    ayt_physics_net=excluded.ayt_physics_net,ayt_chemistry_net=excluded.ayt_chemistry_net,
                    ayt_biology_net=excluded.ayt_biology_net,ayt_literature_net=excluded.ayt_literature_net,
                    ayt_history1_net=excluded.ayt_history1_net,ayt_geography1_net=excluded.ayt_geography1_net,
                    ayt_history2_net=excluded.ayt_history2_net,ayt_geography2_net=excluded.ayt_geography2_net,
                    ayt_philosophy_net=excluded.ayt_philosophy_net,ayt_religion_net=excluded.ayt_religion_net,
                    foreign_language_net=excluded.foreign_language_net,source_payload_checksum=excluded.source_payload_checksum,
                    source_payload=excluded.source_payload,updated_at=CURRENT_TIMESTAMP
                """;
        jdbc.batchUpdate(sourceSql,rows,500,(ps,row)->setNetParameters(ps,row,true));
        String sql="""
                INSERT INTO admission_statistics(admission_option_id,guide_year,minimum_score,average_secondary_score,
                    score_coefficient,tyt_turkish_net,tyt_social_net,tyt_math_net,tyt_science_net,ayt_math_net,
                    ayt_physics_net,ayt_chemistry_net,ayt_biology_net,ayt_literature_net,ayt_history1_net,
                    ayt_geography1_net,ayt_history2_net,ayt_geography2_net,ayt_philosophy_net,ayt_religion_net,
                    foreign_language_net,statistics_source,source_payload_checksum)
                SELECT ao.id,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'YOK_ATLAS_NETLER',?
                FROM admission_options ao WHERE ao.guide_code=? AND ao.deleted_at IS NULL
                ON CONFLICT (admission_option_id,guide_year) DO UPDATE SET
                    minimum_score=COALESCE(excluded.minimum_score,NULLIF(admission_statistics.minimum_score,0)),
                    average_secondary_score=COALESCE(excluded.average_secondary_score,admission_statistics.average_secondary_score),
                    score_coefficient=COALESCE(excluded.score_coefficient,admission_statistics.score_coefficient),
                    tyt_turkish_net=COALESCE(excluded.tyt_turkish_net,admission_statistics.tyt_turkish_net),
                    tyt_social_net=COALESCE(excluded.tyt_social_net,admission_statistics.tyt_social_net),
                    tyt_math_net=COALESCE(excluded.tyt_math_net,admission_statistics.tyt_math_net),
                    tyt_science_net=COALESCE(excluded.tyt_science_net,admission_statistics.tyt_science_net),
                    ayt_math_net=COALESCE(excluded.ayt_math_net,admission_statistics.ayt_math_net),
                    ayt_physics_net=COALESCE(excluded.ayt_physics_net,admission_statistics.ayt_physics_net),
                    ayt_chemistry_net=COALESCE(excluded.ayt_chemistry_net,admission_statistics.ayt_chemistry_net),
                    ayt_biology_net=COALESCE(excluded.ayt_biology_net,admission_statistics.ayt_biology_net),
                    ayt_literature_net=COALESCE(excluded.ayt_literature_net,admission_statistics.ayt_literature_net),
                    ayt_history1_net=COALESCE(excluded.ayt_history1_net,admission_statistics.ayt_history1_net),
                    ayt_geography1_net=COALESCE(excluded.ayt_geography1_net,admission_statistics.ayt_geography1_net),
                    ayt_history2_net=COALESCE(excluded.ayt_history2_net,admission_statistics.ayt_history2_net),
                    ayt_geography2_net=COALESCE(excluded.ayt_geography2_net,admission_statistics.ayt_geography2_net),
                    ayt_philosophy_net=COALESCE(excluded.ayt_philosophy_net,admission_statistics.ayt_philosophy_net),
                    ayt_religion_net=COALESCE(excluded.ayt_religion_net,admission_statistics.ayt_religion_net),
                    foreign_language_net=COALESCE(excluded.foreign_language_net,admission_statistics.foreign_language_net),
                    statistics_source='YOK_ATLAS+NETLER',source_payload_checksum=excluded.source_payload_checksum,
                    updated_at=CURRENT_TIMESTAMP
                """;
        jdbc.batchUpdate(sql,rows,500,(ps,row)->{
            int i=1;
            ps.setInt(i++,row.year());ps.setBigDecimal(i++,row.minimumScore());ps.setBigDecimal(i++,row.averageSecondaryScore());
            ps.setBigDecimal(i++,row.coefficient());ps.setBigDecimal(i++,row.tytTurkish());ps.setBigDecimal(i++,row.tytSocial());
            ps.setBigDecimal(i++,row.tytMath());ps.setBigDecimal(i++,row.tytScience());ps.setBigDecimal(i++,row.aytMath());
            ps.setBigDecimal(i++,row.aytPhysics());ps.setBigDecimal(i++,row.aytChemistry());ps.setBigDecimal(i++,row.aytBiology());
            ps.setBigDecimal(i++,row.aytLiterature());ps.setBigDecimal(i++,row.aytHistory1());ps.setBigDecimal(i++,row.aytGeography1());
            ps.setBigDecimal(i++,row.aytHistory2());ps.setBigDecimal(i++,row.aytGeography2());ps.setBigDecimal(i++,row.aytPhilosophy());
            ps.setBigDecimal(i++,row.aytReligion());ps.setBigDecimal(i++,row.foreignLanguage());ps.setString(i++,row.payloadChecksum());
            ps.setString(i,row.guideCode());
        });
    }

    private static void setNetParameters(java.sql.PreparedStatement ps,YokAtlasNetStats row,boolean includeIdentity)throws SQLException {
        int i=1;
        if(includeIdentity){ps.setString(i++,row.guideCode());ps.setInt(i++,row.year());}
        ps.setBigDecimal(i++,row.minimumScore());ps.setBigDecimal(i++,row.averageSecondaryScore());ps.setBigDecimal(i++,row.coefficient());
        ps.setBigDecimal(i++,row.tytTurkish());ps.setBigDecimal(i++,row.tytSocial());ps.setBigDecimal(i++,row.tytMath());
        ps.setBigDecimal(i++,row.tytScience());ps.setBigDecimal(i++,row.aytMath());ps.setBigDecimal(i++,row.aytPhysics());
        ps.setBigDecimal(i++,row.aytChemistry());ps.setBigDecimal(i++,row.aytBiology());ps.setBigDecimal(i++,row.aytLiterature());
        ps.setBigDecimal(i++,row.aytHistory1());ps.setBigDecimal(i++,row.aytGeography1());ps.setBigDecimal(i++,row.aytHistory2());
        ps.setBigDecimal(i++,row.aytGeography2());ps.setBigDecimal(i++,row.aytPhilosophy());ps.setBigDecimal(i++,row.aytReligion());
        ps.setBigDecimal(i++,row.foreignLanguage());ps.setString(i++,row.payloadChecksum());ps.setString(i,row.sourcePayload());
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
        jdbc.update("""
                UPDATE university_departments ud SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=ud.version+1
                WHERE ud.deleted_at IS NULL AND ud.updated_at<=? AND EXISTS (
                    SELECT 1 FROM programs p JOIN program_families f ON f.id=p.program_family_id
                    WHERE p.id=ud.program_id AND f.source IN ('YOK_ATLAS','TURKIYE_PROGRAMS'))
                """,cutoff);
        jdbc.update("""
                UPDATE departments d SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                WHERE d.deleted_at IS NULL AND EXISTS (
                    SELECT 1 FROM university_departments ud JOIN programs p ON p.id=ud.program_id
                    JOIN program_families f ON f.id=p.program_family_id
                    WHERE ud.department_id=d.id AND f.source IN ('YOK_ATLAS','TURKIYE_PROGRAMS') AND ud.deleted_at IS NOT NULL)
                AND NOT EXISTS (SELECT 1 FROM university_departments active WHERE active.department_id=d.id AND active.deleted_at IS NULL)
                """);
        jdbc.update("""
                UPDATE universities SET deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE catalog_source IN ('YOK_ATLAS','TURKIYE_PROGRAMS') AND deleted_at IS NULL AND updated_at<=?
                """,cutoff);
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
