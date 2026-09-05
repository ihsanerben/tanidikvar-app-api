package com.tanidikvar.api.profile.service;

import com.tanidikvar.api.auth.service.AccountAccessService;
import com.tanidikvar.api.catalog.service.CatalogService;
import com.tanidikvar.api.common.error.DomainException;
import com.tanidikvar.api.profile.dto.*;
import com.tanidikvar.api.profile.entity.*;
import com.tanidikvar.api.profile.mapper.ProfileMapper;
import com.tanidikvar.api.profile.repository.ProfileRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final ProfileRepository profiles;
    private final ProfileMapper mapper;
    private final CatalogService catalog;
    private final AccountAccessService accounts;
    private final Clock clock;
    public ProfileService(ProfileRepository profiles,ProfileMapper mapper,CatalogService catalog,AccountAccessService accounts,Clock clock) {
        this.profiles=profiles; this.mapper=mapper; this.catalog=catalog; this.accounts=accounts; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public ProfileResponse get(UUID id) {
        var p=profiles.find(id).orElse(null);
        return mapper.toResponse(p,p==null || p.deletedAt()!=null || p.universityDepartmentId()==null?null:catalog.education(p.universityDepartmentId()));
    }
    @Transactional
    public ProfileResponse update(UUID id,ProfileRequest request) {
        accounts.lockActive(id);
        var old=profiles.find(id).orElse(null);
        if(request.version()!=(old==null?0:old.version())) throw new DomainException(409,"STALE_VERSION","Profil başka bir ekranda değişmiş. Güncel bilgileri yükle.");
        var fields=new LinkedHashMap<String,String>();
        String first=clean(request.firstName()),last=clean(request.lastName());
        if(first.isEmpty()) fields.put("firstName","Adını yaz.");
        if(last.isEmpty()) fields.put("lastName","Soyadını yaz.");
        if(request.educationStatus()==EducationStatus.YKS_ADAYI) {
            if(request.universityDepartmentId()!=null) fields.put("universityDepartmentId","YKS adayı için üniversite/bölüm seçilmez.");
        } else if(request.universityDepartmentId()==null) fields.put("universityDepartmentId","Üniversiteni ve bölümünü seç.");
        if(request.educationStatus()==EducationStatus.MEZUN) {
            int year=LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul"))).getYear();
            if(request.graduationYear()==null || request.graduationYear()<1900 || request.graduationYear()>year) fields.put("graduationYear","Geçerli bir mezuniyet yılı yaz.");
        } else if(request.graduationYear()!=null) fields.put("graduationYear","Mezuniyet yılı yalnız mezunlar içindir.");
        if(!fields.isEmpty()) throw new DomainException(400,"VALIDATION_FAILED","Profil alanlarını kontrol et.",fields);
        if(request.universityDepartmentId()!=null) {
            boolean newSelection=old==null || old.deletedAt()!=null || !Objects.equals(old.universityDepartmentId(),request.universityDepartmentId());
            catalog.lockEducation(request.universityDepartmentId(),newSelection);
        }
        profiles.save(new UserProfile(id,first,last,request.educationStatus(),request.universityDepartmentId(),request.graduationYear(),
                optional(request.biography()),optional(request.occupation()),optional(request.company()),null,request.version()),old!=null);
        return get(id);
    }
    private String clean(String text) { return text.replaceAll("[\\s\\p{Z}]+"," ").strip(); }
    private String optional(String text) { return text==null || text.isBlank()?null:text.strip(); }
}
