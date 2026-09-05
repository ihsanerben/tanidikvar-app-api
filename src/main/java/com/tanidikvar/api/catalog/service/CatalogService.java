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
    private DomainException missing() { return new DomainException(404,"NOT_FOUND","Kayıt bulunamadı."); }
    @Transactional
    public CatalogResponse create(UUID actor,CatalogKind kind,String name) {
        var account=accounts.lockActive(actor);
        if(account.getAuthority()!=Authority.MANAGER) {
            if(kind!=CatalogKind.TAG || account.getAuthority()!=Authority.ADMIN) throw new DomainException(403,"ACCESS_DENIED","Bu işlem için yetkin yok.");
            interaction.requireCompleted(actor);
        }
        UUID id=UUID.randomUUID(); String clean=CatalogNames.clean(name);
        catalog.create(kind,id,clean,CatalogNames.normalized(clean),actor); catalog.audit(actor,"CREATE",kind.name(),id);
        return mapper.toResponse(catalog.lock(kind,id).orElseThrow(this::missing));
    }
    @Transactional
    public CatalogResponse rename(UUID actor,CatalogKind kind,UUID id,CatalogUpdateRequest request) {
        manager(actor); var current=catalog.lock(kind,id).orElseThrow(this::missing); checkVersion(current.version(),request.version());
        String name=CatalogNames.clean(request.name()); catalog.rename(kind,id,name,CatalogNames.normalized(name)); catalog.audit(actor,"RENAME",kind.name(),id);
        return mapper.toResponse(catalog.lock(kind,id).orElseThrow(this::missing));
    }
    @Transactional
    public CatalogResponse status(UUID actor,CatalogKind kind,UUID id,CatalogStatusRequest request) {
        manager(actor); var current=catalog.lock(kind,id).orElseThrow(this::missing); checkVersion(current.version(),request.version());
        if((current.deletedAt()!=null)!=request.deleted()) {
            catalog.status(kind,id,request.deleted()); catalog.audit(actor,request.deleted()?"SOFT_DELETE":"RESTORE",kind.name(),id);
        }
        return mapper.toResponse(catalog.lock(kind,id).orElseThrow(this::missing));
    }
    @Transactional
    public EducationResponse createEducation(UUID actor,EducationCreateRequest request) {
        manager(actor);
        var university=catalog.lock(CatalogKind.UNIVERSITY,request.universityId()).orElseThrow(this::missing);
        var department=catalog.lock(CatalogKind.DEPARTMENT,request.departmentId()).orElseThrow(this::missing);
        if(university.deletedAt()!=null || department.deletedAt()!=null) throw new DomainException(400,"INACTIVE_EDUCATION","Aktif üniversite ve bölüm seç.");
        UUID id=UUID.randomUUID(); catalog.createEducation(id,university.id(),department.id()); catalog.audit(actor,"CREATE","UNIVERSITY_DEPARTMENT",id);
        return education(id);
    }
    @Transactional
    public EducationResponse educationStatus(UUID actor,UUID id,CatalogStatusRequest request) {
        manager(actor); var current=lockEducation(id,false); checkVersion(current.version(),request.version());
        if(!request.deleted()) {
            var university=catalog.lock(CatalogKind.UNIVERSITY,current.universityId()).orElseThrow(this::missing);
            var department=catalog.lock(CatalogKind.DEPARTMENT,current.departmentId()).orElseThrow(this::missing);
            if(university.deletedAt()!=null || department.deletedAt()!=null) throw new DomainException(400,"INACTIVE_EDUCATION","Önce üniversite ve bölümü etkinleştir.");
        }
        if((current.deletedAt()!=null)!=request.deleted()) {
            catalog.educationStatus(id,request.deleted()); catalog.audit(actor,request.deleted()?"SOFT_DELETE":"RESTORE","UNIVERSITY_DEPARTMENT",id);
        }
        return education(id);
    }
}
