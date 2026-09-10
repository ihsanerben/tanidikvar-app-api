package com.tanidikvar.api.catalog.service;

import com.tanidikvar.api.auth.entity.Authority;
import com.tanidikvar.api.auth.service.AccountAccessService;
import com.tanidikvar.api.catalog.dto.*;
import com.tanidikvar.api.catalog.entity.*;
import com.tanidikvar.api.catalog.mapper.CatalogMapper;
import com.tanidikvar.api.catalog.repository.CatalogRepository;
import com.tanidikvar.api.common.dto.PageResponse;
import com.tanidikvar.api.common.error.DomainException;
import com.tanidikvar.api.profile.service.InteractionPolicy;
import java.util.UUID;
import java.util.LinkedHashMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CatalogService {
    private final CatalogRepository catalog;
    private final CatalogMapper mapper;
    private final AccountAccessService accounts;
    private final InteractionPolicy interaction;
    public CatalogService(CatalogRepository catalog,CatalogMapper mapper,AccountAccessService accounts,InteractionPolicy interaction) {
        this.catalog=catalog; this.mapper=mapper; this.accounts=accounts; this.interaction=interaction;
    }
    private void page(int page,int size) {
        if(page<0 || page>10000 || size<1 || size>100) throw new DomainException(400,"INVALID_REQUEST","Sayfa ve boyut sınırlarını kontrol et.");
    }
    @Transactional(readOnly=true)
    public PageResponse<CatalogResponse> list(CatalogKind kind,String query,boolean includeDeleted,int page,int size) {
        page(page,size); String search=CatalogNames.search(query);
        return new PageResponse<>(catalog.list(kind,search,includeDeleted,page,size).stream().map(mapper::toResponse).toList(),page,size,catalog.count(kind,search,includeDeleted));
    }
    @Transactional(readOnly=true)
    public PageResponse<EducationResponse> educationList(UUID university,String query,boolean includeDeleted,int page,int size) {
        page(page,size); String search=CatalogNames.search(query);
        return new PageResponse<>(catalog.educationList(university,search,includeDeleted,page,size),page,size,catalog.educationCount(university,search,includeDeleted));
    }
    @Transactional(readOnly=true)
    public EducationResponse education(UUID id) { return catalog.education(id).orElseThrow(this::missing); }
    @Transactional(readOnly=true)
    public EducationResponse selection(UUID universityId,UUID departmentId) {
        var university=catalog.find(CatalogKind.UNIVERSITY,universityId).orElseThrow(this::missing);
        var department=catalog.find(CatalogKind.DEPARTMENT,departmentId).orElseThrow(this::missing);
        boolean available=university.deletedAt()==null&&department.deletedAt()==null;
        return new EducationResponse(department.id(),university.id(),university.name(),department.id(),department.name(),available?null:java.time.Instant.EPOCH,available,Math.max(university.version(),department.version()));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public EducationResponse lockEducation(UUID id,boolean requireActive) {
        var relation=education(id);
        catalog.lock(CatalogKind.UNIVERSITY,relation.universityId()).orElseThrow(this::missing);
        catalog.lock(CatalogKind.DEPARTMENT,relation.departmentId()).orElseThrow(this::missing);
        catalog.lockEducationRow(id);
        relation=education(id);
        if(requireActive && !relation.available()) throw new DomainException(400,"INACTIVE_EDUCATION","Bu üniversite/bölüm artık yeni seçimlere açık değil.");
        return relation;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public CatalogResponse lockReference(CatalogKind kind,UUID id,boolean requireActive) {
        var entry=catalog.lock(kind,id).orElseThrow(this::missing);
        if(requireActive && entry.deletedAt()!=null) throw new DomainException(400,"INACTIVE_CATALOG","Aktif bir katalog kaydı seç.");
        return mapper.toResponse(entry);
    }
    private void manager(UUID actor) {
        if(accounts.lockActive(actor).getAuthority()!=Authority.MANAGER) throw new DomainException(403,"ACCESS_DENIED","Bu işlem için Manager yetkisi gerekir.");
    }
    private void checkVersion(long actual,long requested) {
        if(actual!=requested) throw new DomainException(409,"STALE_VERSION","Bu kayıt değişmiş. Güncel bilgileri yükleyip tekrar dene.");
    }
    private String reason(String reason) { if(reason==null||reason.isBlank()||reason.strip().length()>1000)throw new DomainException(400,"REASON_REQUIRED","İşlem gerekçesi gerekiyor.");return reason.strip(); }
    private DomainException missing() { return new DomainException(404,"NOT_FOUND","Kayıt bulunamadı."); }
    @Transactional
    public CatalogResponse create(UUID actor,CatalogKind kind,String name) { return create(actor,kind,name,null); }
    @Transactional
    public CatalogResponse create(UUID actor,CatalogKind kind,String name,String reason) {
        var account=accounts.lockActive(actor);
        if(account.getAuthority()!=Authority.MANAGER) {
            throw new DomainException(403,"ACCESS_DENIED","Bu işlem için Manager yetkisi gerekir.");
        }
        if(account.getAuthority()==Authority.MANAGER) reason=reason(reason);
        UUID id=UUID.randomUUID(); String clean=CatalogNames.clean(name);
        catalog.create(kind,id,clean,CatalogNames.normalized(clean),actor); catalog.audit(actor,"CREATE",kind.name(),id,reason);
        return mapper.toResponse(catalog.lock(kind,id).orElseThrow(this::missing));
    }
    @Transactional
    public CatalogResponse rename(UUID actor,CatalogKind kind,UUID id,CatalogUpdateRequest request) {
        manager(actor); reason(request.reason()); var current=catalog.lock(kind,id).orElseThrow(this::missing); checkVersion(current.version(),request.version());
        String name=CatalogNames.clean(request.name()); if(!current.name().equals(name)){catalog.rename(kind,id,name,CatalogNames.normalized(name)); catalog.audit(actor,"RENAME",kind.name(),id,request.reason().strip());}
        return mapper.toResponse(catalog.lock(kind,id).orElseThrow(this::missing));
    }
    @Transactional
    public CatalogResponse status(UUID actor,CatalogKind kind,UUID id,CatalogStatusRequest request) {
        manager(actor); reason(request.reason()); var current=catalog.lock(kind,id).orElseThrow(this::missing); checkVersion(current.version(),request.version());
        if((current.deletedAt()!=null)!=request.deleted()) {
            catalog.status(kind,id,request.deleted()); catalog.audit(actor,request.deleted()?"SOFT_DELETE":"RESTORE",kind.name(),id,request.reason().strip());
        }
        return mapper.toResponse(catalog.lock(kind,id).orElseThrow(this::missing));
    }
    @Transactional
    public void deleteUniversity(UUID actor,UUID id,CatalogDeleteRequest request) {
        manager(actor); String cleanReason=reason(request.reason());
        var current=catalog.lock(CatalogKind.UNIVERSITY,id).orElseThrow(this::missing);
        checkVersion(current.version(),request.version());
        if(current.deletedAt()==null) throw new DomainException(409,"CATALOG_ACTIVE","Üniversiteyi silmeden önce pasife al.");
        if(catalog.universityInUse(id)) throw new DomainException(409,"CATALOG_IN_USE","Bu üniversite geçmiş kayıtlarda kullanıldığı için silinemez.");
        try {
            catalog.deleteUniversity(id);
        } catch(DataIntegrityViolationException exception) {
            throw new DomainException(409,"CATALOG_IN_USE","Bu üniversite geçmiş kayıtlarda kullanıldığı için silinemez.");
        }
        catalog.audit(actor,"HARD_DELETE","UNIVERSITY",id,cleanReason);
    }
    @Transactional
    public EducationResponse createEducation(UUID actor,EducationCreateRequest request) {
        manager(actor); reason(request.reason());
        var university=catalog.lock(CatalogKind.UNIVERSITY,request.universityId()).orElseThrow(this::missing);
        var department=catalog.lock(CatalogKind.DEPARTMENT,request.departmentId()).orElseThrow(this::missing);
        if(university.deletedAt()!=null || department.deletedAt()!=null) throw new DomainException(400,"INACTIVE_EDUCATION","Aktif üniversite ve bölüm seç.");
        UUID id=UUID.randomUUID(); catalog.createEducation(id,university.id(),department.id()); catalog.audit(actor,"CREATE","UNIVERSITY_DEPARTMENT",id,request.reason().strip());
        return education(id);
    }
    @Transactional
    public EducationResponse educationStatus(UUID actor,UUID id,CatalogStatusRequest request) {
        manager(actor); reason(request.reason()); var current=lockEducation(id,false); checkVersion(current.version(),request.version());
        if(!request.deleted()) {
            var university=catalog.lock(CatalogKind.UNIVERSITY,current.universityId()).orElseThrow(this::missing);
            var department=catalog.lock(CatalogKind.DEPARTMENT,current.departmentId()).orElseThrow(this::missing);
            if(university.deletedAt()!=null || department.deletedAt()!=null) throw new DomainException(400,"INACTIVE_EDUCATION","Önce üniversite ve bölümü etkinleştir.");
        }
        if((current.deletedAt()!=null)!=request.deleted()) {
            catalog.educationStatus(id,request.deleted()); catalog.audit(actor,request.deleted()?"SOFT_DELETE":"RESTORE","UNIVERSITY_DEPARTMENT",id,request.reason().strip());
        }
        return education(id);
    }
    @Transactional
    public CatalogBulkImportResponse bulkImport(UUID actor,CatalogBulkImportRequest request) {
        manager(actor); String reason=reason(request.reason());
        var universities=new LinkedHashMap<String,CatalogResponse>();
        var departments=new LinkedHashMap<String,CatalogResponse>();
        int universityCreates=0,departmentCreates=0,skipped=0;
        for(String raw:request.universities()) {
            String name=CatalogNames.clean(raw),normalized=CatalogNames.normalized(name);
            var existing=catalog.byNormalizedName(CatalogKind.UNIVERSITY,normalized);
            if(existing.isPresent()) { universities.put(normalized,mapper.toResponse(existing.get())); skipped++; }
            else { UUID id=UUID.randomUUID();catalog.create(CatalogKind.UNIVERSITY,id,name,normalized,actor);catalog.audit(actor,"BULK_CREATE","UNIVERSITY",id,reason);universities.put(normalized,mapper.toResponse(catalog.lock(CatalogKind.UNIVERSITY,id).orElseThrow(this::missing)));universityCreates++; }
        }
        for(String raw:request.departments()) {
            String name=CatalogNames.clean(raw),normalized=CatalogNames.normalized(name);
            var existing=catalog.byNormalizedName(CatalogKind.DEPARTMENT,normalized);
            if(existing.isPresent()) { departments.put(normalized,mapper.toResponse(existing.get())); skipped++; }
            else { UUID id=UUID.randomUUID();catalog.create(CatalogKind.DEPARTMENT,id,name,normalized,actor);catalog.audit(actor,"BULK_CREATE","DEPARTMENT",id,reason);departments.put(normalized,mapper.toResponse(catalog.lock(CatalogKind.DEPARTMENT,id).orElseThrow(this::missing)));departmentCreates++; }
        }
        return new CatalogBulkImportResponse(universityCreates,departmentCreates,skipped);
    }
    @Transactional
    public TagBulkImportResponse bulkImportTags(UUID actor,TagBulkImportRequest request) {
        manager(actor); String reason=reason(request.reason());
        int created=0,skipped=0;
        for(String raw:request.tags()) {
            String name=CatalogNames.clean(raw),normalized=CatalogNames.normalized(name);
            if(catalog.byNormalizedName(CatalogKind.TAG,normalized).isPresent()) { skipped++; continue; }
            UUID id=UUID.randomUUID();
            catalog.create(CatalogKind.TAG,id,name,normalized,actor);
            catalog.audit(actor,"BULK_CREATE","TAG",id,reason);
            created++;
        }
        return new TagBulkImportResponse(created,skipped);
    }
}
