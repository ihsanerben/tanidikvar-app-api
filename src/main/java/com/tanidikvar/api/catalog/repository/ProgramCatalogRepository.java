package com.tanidikvar.api.catalog.repository;

import com.tanidikvar.api.catalog.dto.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProgramCatalogRepository {
    private final JdbcTemplate jdbc;
    public ProgramCatalogRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    private static final String ACTIVE="""
            FROM programs p JOIN universities u ON u.id=p.university_id
            JOIN program_families pf ON pf.id=p.program_family_id
            JOIN admission_options ao ON ao.program_id=p.id AND ao.deleted_at IS NULL
            LEFT JOIN academic_units au ON au.id=ao.academic_unit_id AND au.deleted_at IS NULL
            LEFT JOIN admission_statistics current_stats ON current_stats.admission_option_id=ao.id AND current_stats.guide_year=?
            LEFT JOIN university_departments ud ON ud.program_id=p.id AND ud.deleted_at IS NULL
            WHERE p.deleted_at IS NULL AND u.deleted_at IS NULL AND pf.deleted_at IS NULL
            """;
    private static final String FILTER="""
            AND (strpos(search_fold(p.display_name),search_fold(?))>0 OR strpos(search_fold(u.name),search_fold(?))>0 OR strpos(search_fold(coalesce(au.name,'')),search_fold(?))>0)
            AND strpos(search_fold(p.display_name),search_fold(?))>0
            AND strpos(search_fold(u.name),search_fold(?))>0
            AND (?='' OR search_fold(coalesce(u.city,''))=search_fold(?))
            AND (?='' OR u.institution_type=?) AND (?='' OR pf.degree_level=?)
            AND (?='' OR ao.score_type=?) AND (?::integer IS NULL OR ao.duration_years=?)
            AND (?::integer IS NULL OR current_stats.success_rank>=?) AND (?::integer IS NULL OR current_stats.success_rank<=?)
            AND (?::numeric IS NULL OR current_stats.minimum_score>=?) AND (?::numeric IS NULL OR current_stats.minimum_score<=?)
            AND (?::boolean IS NULL OR (? AND current_stats.quota IS NOT NULL AND current_stats.placed>=current_stats.quota) OR (NOT ? AND current_stats.quota IS NOT NULL AND coalesce(current_stats.placed,0)<current_stats.quota))
            AND (?='' OR strpos(search_fold(coalesce(au.name,'')),search_fold(?))>0)
            AND (?::uuid IS NULL OR p.university_id=?)
            """;
    private static final String SUMMARY="""
            SELECT p.id,(array_agg(DISTINCT ud.id) FILTER (WHERE ud.id IS NOT NULL))[1] education_id,
              (array_agg(DISTINCT ud.department_id) FILTER (WHERE ud.department_id IS NOT NULL))[1] department_id,
              p.university_id,u.name university_name,u.city,u.institution_type,p.display_name,pf.degree_level,
              string_agg(DISTINCT ao.guide_code,'|' ORDER BY ao.guide_code) program_codes,
              string_agg(DISTINCT au.name,'|' ORDER BY au.name) FILTER (WHERE au.name IS NOT NULL) faculties,
              string_agg(DISTINCT ao.score_type,'|' ORDER BY ao.score_type) FILTER (WHERE ao.score_type IS NOT NULL) score_types,
              min(ao.duration_years) duration_years,count(DISTINCT ao.id) option_count,
              min(current_stats.success_rank) current_best_rank,min(current_stats.minimum_score) current_minimum_score,
              coalesce(sum(current_stats.quota) FILTER (WHERE current_stats.quota IS NOT NULL),0) current_quota,
              coalesce(sum(current_stats.placed) FILTER (WHERE current_stats.placed IS NOT NULL),0) current_placed
            """;
    private static final String GROUP=" GROUP BY p.id,p.university_id,u.name,u.city,u.institution_type,p.display_name,pf.degree_level ";

    public List<ProgramSummaryResponse> list(String query,String programName,String universityName,String city,String type,String level,String scoreType,
            Integer duration,Integer rankFrom,Integer rankTo,java.math.BigDecimal scoreFrom,java.math.BigDecimal scoreTo,Boolean filled,Integer year,String faculty,UUID universityId,int page,int size,String sort){
        String order=switch(sort){case "RANK"->"current_best_rank NULLS LAST,p.display_name";case "SCORE"->"current_minimum_score DESC NULLS LAST,p.display_name";case "QUOTA"->"current_quota DESC,p.display_name";default->"p.display_name,u.name";};
        return jdbc.query(SUMMARY+ACTIVE+FILTER+GROUP+" ORDER BY "+order+" LIMIT ? OFFSET ?",this::mapSummary,
                params(query,programName,universityName,city,type,level,scoreType,duration,rankFrom,rankTo,scoreFrom,scoreTo,filled,year,faculty,universityId,size,page*size));
    }
    public long count(String query,String programName,String universityName,String city,String type,String level,String scoreType,Integer duration,Integer rankFrom,Integer rankTo,java.math.BigDecimal scoreFrom,java.math.BigDecimal scoreTo,Boolean filled,Integer year,String faculty,UUID universityId){
        return jdbc.queryForObject("SELECT count(DISTINCT p.id) "+ACTIVE+FILTER,Long.class,
                params(query,programName,universityName,city,type,level,scoreType,duration,rankFrom,rankTo,scoreFrom,scoreTo,filled,year,faculty,universityId));
    }
    private Object[] params(String q,String programName,String universityName,String city,String type,String level,String score,Integer duration,Integer from,Integer to,java.math.BigDecimal scoreFrom,java.math.BigDecimal scoreTo,Boolean filled,Integer year,String faculty,UUID universityId,Object...tail){
        List<Object> values=new ArrayList<>();values.add(year);values.addAll(List.of(q,q,q,programName,universityName,city,city,type,type,level,level,score,score));
        values.add(duration);values.add(duration);values.add(from);values.add(from);values.add(to);values.add(to);values.add(scoreFrom);values.add(scoreFrom);values.add(scoreTo);values.add(scoreTo);values.add(filled);values.add(Boolean.TRUE.equals(filled));values.add(Boolean.TRUE.equals(filled));values.add(faculty);values.add(faculty);values.add(universityId);values.add(universityId);
        values.addAll(Arrays.asList(tail));return values.toArray();
    }
    public Optional<ProgramSummaryResponse> summary(UUID id){return jdbc.query(SUMMARY+ACTIVE+" AND p.id=? "+GROUP,this::mapSummary,2026,id).stream().findFirst();}
    public List<ProgramAcademicDetailsResponse> academicDetails(UUID program){
        return jdbc.query("""
                SELECT d.academic_unit_id,au.name faculty,d.professor_count,d.associate_professor_count,
                  d.doctor_faculty_member_count,d.research_assistant_count,d.accreditation_code,
                  d.accreditation_description,d.minimum_success_rank,d.tyc_qualified
                FROM program_academic_details d
                LEFT JOIN academic_units au ON au.id=d.academic_unit_id
                WHERE d.program_id=? ORDER BY au.name NULLS LAST
                """,(r,n)->new ProgramAcademicDetailsResponse(r.getObject("academic_unit_id",UUID.class),r.getString("faculty"),
                        r.getObject("professor_count",Integer.class),r.getObject("associate_professor_count",Integer.class),
                        r.getObject("doctor_faculty_member_count",Integer.class),r.getObject("research_assistant_count",Integer.class),
                        r.getString("accreditation_code"),r.getString("accreditation_description"),
                        r.getObject("minimum_success_rank",Integer.class),r.getObject("tyc_qualified",Boolean.class)),program);
    }
    public List<AdmissionOptionResponse> options(UUID program){
        var options=jdbc.query("""
                SELECT ao.id,ao.guide_code,au.name faculty,ao.score_type,ao.education_type,ao.language,
                  ao.scholarship,ao.special_quota_type,ao.duration_years,ao.annual_fee
                FROM admission_options ao LEFT JOIN academic_units au ON au.id=ao.academic_unit_id
                WHERE ao.program_id=? AND ao.deleted_at IS NULL ORDER BY ao.guide_code
                """,(r,n)->new OptionRow(r.getObject("id",UUID.class),r.getString("guide_code"),r.getString("faculty"),
                        r.getString("score_type"),r.getString("education_type"),r.getString("language"),
                        r.getString("scholarship"),r.getString("special_quota_type"),r.getObject("duration_years",Integer.class),
                        r.getBigDecimal("annual_fee")),program);
        if(options.isEmpty())return List.of();
        var statistics=new HashMap<UUID,List<AdmissionStatisticsResponse>>();
        jdbc.query("""
                SELECT s.admission_option_id,s.guide_year,s.quota,s.placed,s.minimum_score,s.maximum_score,s.success_rank,
                  s.placed_male,s.placed_female,s.average_secondary_score,s.total_preferences,s.demand_per_quota,s.average_preference_rank
                  ,s.score_coefficient,s.tyt_turkish_net,s.tyt_social_net,s.tyt_math_net,s.tyt_science_net,
                  s.ayt_math_net,s.ayt_physics_net,s.ayt_chemistry_net,s.ayt_biology_net,s.ayt_literature_net,
                  s.ayt_history1_net,s.ayt_geography1_net,s.ayt_history2_net,s.ayt_geography2_net,
                  s.ayt_philosophy_net,s.ayt_religion_net,s.foreign_language_net
                FROM admission_statistics s JOIN admission_options ao ON ao.id=s.admission_option_id
                WHERE ao.program_id=? AND ao.deleted_at IS NULL ORDER BY s.admission_option_id,s.guide_year DESC
                """,r->{UUID optionId=r.getObject(1,UUID.class);statistics.computeIfAbsent(optionId,key->new ArrayList<>()).add(statistic(r,2));},program);
        return options.stream().map(option->new AdmissionOptionResponse(option.id,option.code,option.faculty,option.scoreType,
                option.educationType,option.language,option.scholarship,option.specialQuotaType,option.duration,
                option.annualFee,
                List.copyOf(statistics.getOrDefault(option.id,List.of())))).toList();
    }
    public long scalar(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    public List<LabelCountResponse> distribution(String sql,Object...args){return jdbc.query(sql,(r,n)->new LabelCountResponse(r.getString(1),r.getLong(2)),args);}
    public List<YearCatalogStatisticsResponse> yearly(UUID university){
        String filter=university==null?"":" AND p.university_id=?";
        return jdbc.query("""
                SELECT s.guide_year,count(DISTINCT ao.id),coalesce(sum(s.quota),0),coalesce(sum(s.placed),0),
                  CASE WHEN sum(s.quota)>0 THEN round(sum(s.placed)::numeric*100/sum(s.quota),2) END,coalesce(sum(s.total_preferences),0)
                FROM admission_statistics s JOIN admission_options ao ON ao.id=s.admission_option_id AND ao.deleted_at IS NULL
                JOIN programs p ON p.id=ao.program_id AND p.deleted_at IS NULL JOIN universities u ON u.id=p.university_id AND u.deleted_at IS NULL
                WHERE 1=1"""+filter+" GROUP BY s.guide_year ORDER BY s.guide_year",(r,n)->new YearCatalogStatisticsResponse(r.getInt(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getBigDecimal(5),r.getLong(6)),university==null?new Object[]{}:new Object[]{university});
    }
    public Instant lastSync(){return jdbc.query("SELECT max(completed_at) FROM catalog_sync_runs WHERE source IN ('YOK_ATLAS','TURKIYE_PROGRAMS') AND operation='APPLY' AND status='SUCCEEDED'",(r,n)->r.getTimestamp(1)==null?null:r.getTimestamp(1).toInstant()).getFirst();}
    public List<ProgramSummaryResponse> best(UUID university){return jdbc.query(SUMMARY+ACTIVE+" AND p.university_id=? "+GROUP+" ORDER BY current_best_rank NULLS LAST LIMIT 10",this::mapSummary,2026,university);}
    private ProgramSummaryResponse mapSummary(ResultSet r,int n)throws SQLException{return new ProgramSummaryResponse(r.getObject("id",UUID.class),r.getObject("education_id",UUID.class),r.getObject("department_id",UUID.class),r.getObject("university_id",UUID.class),r.getString("university_name"),r.getString("city"),r.getString("institution_type"),r.getString("display_name"),r.getString("degree_level"),split(r.getString("program_codes")),split(r.getString("faculties")),split(r.getString("score_types")),r.getObject("duration_years",Integer.class),r.getLong("option_count"),r.getObject("current_best_rank",Integer.class),r.getBigDecimal("current_minimum_score"),r.getLong("current_quota"),r.getLong("current_placed"));}
    private AdmissionStatisticsResponse statistic(ResultSet r,int offset)throws SQLException{return new AdmissionStatisticsResponse(r.getInt(offset),r.getObject(offset+1,Integer.class),r.getObject(offset+2,Integer.class),r.getBigDecimal(offset+3),r.getBigDecimal(offset+4),r.getObject(offset+5,Integer.class),r.getObject(offset+6,Integer.class),r.getObject(offset+7,Integer.class),r.getBigDecimal(offset+8),r.getObject(offset+9,Integer.class),r.getBigDecimal(offset+10),r.getBigDecimal(offset+11),r.getBigDecimal(offset+12),r.getBigDecimal(offset+13),r.getBigDecimal(offset+14),r.getBigDecimal(offset+15),r.getBigDecimal(offset+16),r.getBigDecimal(offset+17),r.getBigDecimal(offset+18),r.getBigDecimal(offset+19),r.getBigDecimal(offset+20),r.getBigDecimal(offset+21),r.getBigDecimal(offset+22),r.getBigDecimal(offset+23),r.getBigDecimal(offset+24),r.getBigDecimal(offset+25),r.getBigDecimal(offset+26),r.getBigDecimal(offset+27),r.getBigDecimal(offset+28));}
    private static List<String> split(String value){return value==null?List.of():List.of(value.split("\\|"));}
    private record OptionRow(UUID id,String code,String faculty,String scoreType,String educationType,
            String language,String scholarship,String specialQuotaType,Integer duration,java.math.BigDecimal annualFee){}
}
