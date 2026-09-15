package com.tanidikvar.api.catalog.service;

import com.tanidikvar.api.catalog.dto.*;
import com.tanidikvar.api.catalog.repository.ProgramCatalogRepository;
import com.tanidikvar.api.common.dto.PageResponse;
import com.tanidikvar.api.common.error.DomainException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramCatalogService {
    private static final Set<String> TYPES=Set.of("","DEVLET","VAKIF","KKTC","YURT_DISI");
    private static final Set<String> LEVELS=Set.of("","LISANS","ONLISANS");
    private static final Set<String> SCORES=Set.of("","TYT","SAY","EA","SÖZ","DİL");
    private static final Set<String> SORTS=Set.of("NAME","RANK","SCORE","QUOTA");
    private final ProgramCatalogRepository repository;
    public ProgramCatalogService(ProgramCatalogRepository repository){this.repository=repository;}

    @Transactional(readOnly=true)
    public PageResponse<ProgramSummaryResponse> list(String query,String city,String type,String level,String score,
            Integer duration,Integer rankFrom,Integer rankTo,String faculty,UUID universityId,String sort,int page,int size){
        if(page<0||page>10000||size<1||size>100)bad();String q=CatalogNames.search(query),c=CatalogNames.search(city),f=CatalogNames.search(faculty);
        String t=upper(type),l=upper(level),s=upper(score),order=upper(sort);if(!TYPES.contains(t)||!LEVELS.contains(l)||!SCORES.contains(s)||!SORTS.contains(order))bad();
        if(duration!=null&&(duration<1||duration>10)||rankFrom!=null&&rankFrom<1||rankTo!=null&&rankTo<1||rankFrom!=null&&rankTo!=null&&rankFrom>rankTo)bad();
        return new PageResponse<>(repository.list(q,c,t,l,s,duration,rankFrom,rankTo,f,universityId,page,size,order),page,size,
                repository.count(q,c,t,l,s,duration,rankFrom,rankTo,f,universityId));
    }
    @Transactional(readOnly=true) public ProgramDetailResponse detail(UUID id){var summary=repository.summary(id).orElseThrow(this::missing);return new ProgramDetailResponse(summary,repository.options(id));}
    @Transactional(readOnly=true) public CatalogOverviewResponse overview(){return new CatalogOverviewResponse(
            repository.scalar("SELECT count(*) FROM universities WHERE deleted_at IS NULL"),repository.scalar("SELECT count(*) FROM programs WHERE deleted_at IS NULL"),
            repository.scalar("SELECT count(*) FROM admission_options WHERE deleted_at IS NULL"),repository.scalar("SELECT count(*) FROM admission_statistics"),
            repository.scalar("SELECT count(DISTINCT admission_option_id) FROM admission_statistics WHERE guide_year=2025 AND success_rank IS NOT NULL"),
            repository.distribution("SELECT institution_type,count(*) FROM universities WHERE deleted_at IS NULL GROUP BY institution_type ORDER BY count(*) DESC"),
            repository.distribution("SELECT coalesce(city,'Belirtilmemiş'),count(*) FROM universities WHERE deleted_at IS NULL GROUP BY city ORDER BY count(*) DESC,city"),
            repository.distribution("SELECT pf.degree_level,count(*) FROM programs p JOIN program_families pf ON pf.id=p.program_family_id WHERE p.deleted_at IS NULL AND pf.deleted_at IS NULL GROUP BY pf.degree_level ORDER BY pf.degree_level"),
            repository.distribution("SELECT coalesce(score_type,'Belirtilmemiş'),count(*) FROM admission_options WHERE deleted_at IS NULL GROUP BY score_type ORDER BY count(*) DESC"),
            repository.yearly(null),repository.lastSync());}
    @Transactional(readOnly=true) public UniversityCatalogStatisticsResponse university(UUID id){
        long exists=repository.scalar("SELECT count(*) FROM universities WHERE id=? AND deleted_at IS NULL",id);if(exists==0)throw missing();
        return new UniversityCatalogStatisticsResponse(id,
                repository.scalar("SELECT count(DISTINCT academic_unit_id) FROM admission_options ao JOIN programs p ON p.id=ao.program_id WHERE p.university_id=? AND p.deleted_at IS NULL AND ao.deleted_at IS NULL AND academic_unit_id IS NOT NULL",id),
                repository.scalar("SELECT count(*) FROM programs WHERE university_id=? AND deleted_at IS NULL",id),
                repository.scalar("SELECT count(*) FROM admission_options ao JOIN programs p ON p.id=ao.program_id WHERE p.university_id=? AND p.deleted_at IS NULL AND ao.deleted_at IS NULL",id),
                repository.distribution("SELECT pf.degree_level,count(DISTINCT p.id) FROM programs p JOIN program_families pf ON pf.id=p.program_family_id WHERE p.university_id=? AND p.deleted_at IS NULL GROUP BY pf.degree_level ORDER BY pf.degree_level",id),
                repository.distribution("SELECT coalesce(ao.score_type,'Belirtilmemiş'),count(*) FROM admission_options ao JOIN programs p ON p.id=ao.program_id WHERE p.university_id=? AND p.deleted_at IS NULL AND ao.deleted_at IS NULL GROUP BY ao.score_type ORDER BY count(*) DESC",id),
                repository.yearly(id),repository.best(id));}
    private static String upper(String value){return value==null?"":value.strip().toUpperCase(Locale.forLanguageTag("tr"));}
    private void bad(){throw new DomainException(400,"INVALID_REQUEST","Program filtrelerini kontrol et.");}
    private DomainException missing(){return new DomainException(404,"NOT_FOUND","Program bulunamadı.");}
}
