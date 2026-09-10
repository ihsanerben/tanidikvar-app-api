package com.tanidikvar.api.profile.service;

import com.tanidikvar.api.auth.service.AccountAccessService;
import com.tanidikvar.api.catalog.service.CatalogService;
import com.tanidikvar.api.catalog.entity.CatalogKind;
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
        return mapper.toResponse(p,p==null || p.deletedAt()!=null || p.universityId()==null?null:catalog.selection(p.universityId(),p.departmentId()));
    }
    @Transactional
    public ProfileResponse update(UUID id,ProfileRequest request) {
        if(accounts.lockActive(id).getAuthority()==com.tanidikvar.api.auth.entity.Authority.MANAGER) throw new DomainException(403,"ACCESS_DENIED","Yönetim hesabı ayarlarını kullan.");
        var old=profiles.find(id).orElse(null);
        if(request.version()!=(old==null?0:old.version())) throw new DomainException(409,"STALE_VERSION","Profil başka bir ekranda değişmiş. Güncel bilgileri yükle.");
        var fields=new LinkedHashMap<String,String>();
        String first=clean(request.firstName()),last=clean(request.lastName());
        if(first.isEmpty()) fields.put("firstName","Adını yaz.");
        if(last.isEmpty()) fields.put("lastName","Soyadını yaz.");
        if(request.educationStatus()==EducationStatus.YKS_ADAYI) {
            if(request.universityId()!=null||request.departmentId()!=null) fields.put("departmentId","YKS adayı için üniversite/bölüm seçilmez.");
        } else { if(request.universityId()==null)fields.put("universityId","Üniversiteni seç.");if(request.departmentId()==null)fields.put("departmentId","Bölümünü seç."); }
        if(request.educationStatus()==EducationStatus.MEZUN) {
            int year=LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul"))).getYear();
            if(request.graduationYear()==null || request.graduationYear()<1900 || request.graduationYear()>year) fields.put("graduationYear","Geçerli bir mezuniyet yılı yaz.");
        } else if(request.graduationYear()!=null) fields.put("graduationYear","Mezuniyet yılı yalnız mezunlar içindir.");
        String linkedin=profileUrl(request.linkedinUrl(),"linkedinUrl",true,fields),portfolio=profileUrl(request.portfolioUrl(),"portfolioUrl",false,fields);
        if(!fields.isEmpty()) throw new DomainException(400,"VALIDATION_FAILED","Profil alanlarını kontrol et.",fields);
        if(request.universityId()!=null)catalog.lockReference(CatalogKind.UNIVERSITY,request.universityId(),old==null||!Objects.equals(old.universityId(),request.universityId()));
        if(request.departmentId()!=null)catalog.lockReference(CatalogKind.DEPARTMENT,request.departmentId(),old==null||!Objects.equals(old.departmentId(),request.departmentId()));
        profiles.save(new UserProfile(id,first,last,request.educationStatus(),request.universityId(),request.departmentId(),request.graduationYear(),
                optional(request.biography()),optional(request.occupation()),optional(request.company()),linkedin,portfolio,null,request.version()),old!=null);
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
