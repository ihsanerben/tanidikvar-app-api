package com.tanidikvar.api.profile.service;

import com.tanidikvar.api.auth.service.AccountAccessService;
import com.tanidikvar.api.catalog.service.CatalogService;
import com.tanidikvar.api.catalog.service.ProgramCatalogService;
import com.tanidikvar.api.catalog.entity.CatalogKind;
import com.tanidikvar.api.common.error.DomainException;
import com.tanidikvar.api.profile.dto.*;
import com.tanidikvar.api.profile.entity.*;
import com.tanidikvar.api.profile.mapper.ProfileMapper;
import com.tanidikvar.api.profile.repository.ProfileRepository;
import com.tanidikvar.api.application.repository.ApplicationRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final ProfileRepository profiles;
    private final ProfileMapper mapper;
    private final CatalogService catalog;
    private final ProgramCatalogService programs;
    private final AccountAccessService accounts;
    private final Clock clock;
    private final ApplicationRepository applications;
    public ProfileService(ProfileRepository profiles,ProfileMapper mapper,CatalogService catalog,ProgramCatalogService programs,AccountAccessService accounts,Clock clock,ApplicationRepository applications) {
        this.profiles=profiles; this.mapper=mapper; this.catalog=catalog; this.programs=programs; this.accounts=accounts; this.clock=clock; this.applications=applications;
    }
    @Transactional(readOnly=true)
    public ProfileResponse get(UUID id) {
        var p=profiles.find(id).orElse(null);
        return mapper.toResponse(p,p==null || p.deletedAt()!=null || p.universityId()==null?null:catalog.selection(p.universityId(),p.departmentId()));
    }
    @Transactional
    public ProfileResponse update(UUID id,ProfileRequest request) {
        var account=accounts.lockActive(id);
        if(account.getAuthority()==com.tanidikvar.api.auth.entity.Authority.MANAGER) throw new DomainException(403,"ACCESS_DENIED","Yönetim hesabı ayarlarını kullan.");
        var old=profiles.find(id).orElse(null);
        if(request.version()!=(old==null?0:old.version())) throw new DomainException(409,"STALE_VERSION","Profil başka bir ekranda değişmiş. Güncel bilgileri yükle.");
        var fields=new LinkedHashMap<String,String>();
        String first=clean(request.firstName()),last=clean(request.lastName());
        if(first.isEmpty()) fields.put("firstName","Adını yaz.");
        if(last.isEmpty()) fields.put("lastName","Soyadını yaz.");
        if(request.educationStatus()==EducationStatus.YKS_ADAYI) {
            if(request.universityId()!=null||request.programId()!=null||request.departmentId()!=null) fields.put("programId","YKS adayı için üniversite/program seçilmez.");
        } else { if(request.universityId()==null)fields.put("universityId","Üniversiteni seç.");if(request.programId()==null&&request.departmentId()==null)fields.put("programId","Programını seç."); }
        if(request.educationStatus()==EducationStatus.MEZUN) {
            int year=LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul"))).getYear();
            if(request.graduationYear()==null || request.graduationYear()<1900 || request.graduationYear()>year) fields.put("graduationYear","Geçerli bir mezuniyet yılı yaz.");
        } else if(request.graduationYear()!=null) fields.put("graduationYear","Mezuniyet yılı yalnız mezunlar içindir.");
        if(request.educationStatus()==EducationStatus.UNIVERSITE_OGRENCISI) {
            if(request.classYear()!=null&&(request.classYear()<1||request.classYear()>8)) fields.put("classYear","Sınıf 1 ile 8 arasında olmalı.");
        } else if(request.classYear()!=null) fields.put("classYear","Sınıf yalnız üniversite öğrencileri içindir.");
        String linkedin=profileUrl(request.linkedinUrl(),"linkedinUrl",true,fields),portfolio=profileUrl(request.portfolioUrl(),"portfolioUrl",false,fields);
        if(!fields.isEmpty()) throw new DomainException(400,"VALIDATION_FAILED","Profil alanlarını kontrol et.",fields);
        if(request.universityId()!=null)catalog.lockReference(CatalogKind.UNIVERSITY,request.universityId(),old==null||!Objects.equals(old.universityId(),request.universityId()));
        UUID programId=request.programId(),departmentId=request.departmentId();
        if(programId!=null){var selected=programs.detail(programId).summary();if(!selected.universityId().equals(request.universityId()))fields.put("programId","Program seçilen üniversiteye ait değil.");departmentId=selected.departmentId();}
        if(!fields.isEmpty()) throw new DomainException(400,"VALIDATION_FAILED","Profil alanlarını kontrol et.",fields);
        if(departmentId!=null)catalog.lockReference(CatalogKind.DEPARTMENT,departmentId,old==null||!Objects.equals(old.departmentId(),departmentId));
        profiles.save(new UserProfile(id,first,last,request.educationStatus(),request.universityId(),programId,departmentId,request.classYear(),request.graduationYear(),
                optional(request.biography()),optional(request.occupation()),optional(request.company()),linkedin,portfolio,null,request.version()),old!=null);
        if(account.getAuthority()==com.tanidikvar.api.auth.entity.Authority.TANIDIK&&old!=null&&!Objects.equals(old.universityId(),request.universityId())){
            account.revokeAdmin(clock.instant());
            applications.insert(UUID.randomUUID(),id,UUID.randomUUID(),get(id),"Üniversite bilgisi değiştiği için Tanıdık statüsü yeniden doğrulanmalıdır.",null,null);
        }
        return get(id);
    }
    private String profileUrl(String value,String field,boolean linkedin,Map<String,String> errors) {
        String text=optional(value);if(text==null)return null;
        try {var uri=new java.net.URI(text);String host=uri.getHost();
            if(!Set.of("http","https").contains(uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT)) || host==null || uri.getRawUserInfo()!=null || uri.getPort()!=-1 && uri.getPort()!=80 && uri.getPort()!=443 || text.contains("\\") || linkedin && !(host.equalsIgnoreCase("linkedin.com") || host.toLowerCase(Locale.ROOT).endsWith(".linkedin.com"))) throw new IllegalArgumentException();
            if(uri.toASCIIString().length()>2048)throw new IllegalArgumentException();return uri.toASCIIString();
        } catch(java.net.URISyntaxException|IllegalArgumentException e){errors.put(field,linkedin?"Geçerli bir LinkedIn bağlantısı yaz (https://www.linkedin.com/…).":"http:// veya https:// ile başlayan geçerli bir site bağlantısı yaz.");return null;}
    }
    private String clean(String text) { return text.replaceAll("[\\s\\p{Z}]+"," ").strip(); }
    private String optional(String text) { return text==null || text.isBlank()?null:text.strip(); }
}
