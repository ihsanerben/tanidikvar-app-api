package com.tanidikvar.api.management.service;
import com.tanidikvar.api.management.repository.ManagementRepository;
import com.tanidikvar.api.management.mapper.ManagementMapper;
import com.tanidikvar.api.management.entity.ManagedContent;
import com.tanidikvar.api.management.dto.*;
import com.tanidikvar.api.auth.service.*;
import com.tanidikvar.api.auth.entity.Authority;
import com.tanidikvar.api.application.service.ApplicationService;
import com.tanidikvar.api.common.dto.*;
import com.tanidikvar.api.common.error.DomainException;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class ManagementService {
 private final ManagementRepository repository;private final ManagementMapper mapper;private final AccountAccessService accounts;private final AccountLifecycleService lifecycle;private final ApplicationService applications;private final Clock clock;
 public ManagementService(ManagementRepository repository,ManagementMapper mapper,AccountAccessService accounts,AccountLifecycleService lifecycle,ApplicationService applications,Clock clock){this.repository=repository;this.mapper=mapper;this.accounts=accounts;this.lifecycle=lifecycle;this.applications=applications;this.clock=clock;}
 private DomainException missing(){return new DomainException(404,"NOT_FOUND","Kayıt bulunamadı.");}
 private DomainException denied(){return new DomainException(403,"ACCESS_DENIED","Bu işlem için Manager yetkisi gerekiyor. Manager hesapları pasifleştirilemez.");}
 private void manager(UUID actor){if(accounts.lockActive(actor).getAuthority()!=Authority.MANAGER)throw denied();}
 private void reader(UUID actor){var a=repository.user(actor).orElseThrow(this::denied);if(a.deletedAt()!=null||!a.emailVerified()||!a.authority().equals("MANAGER"))throw denied();}
 private String status(String status){if(!Set.of("ALL","VISIBLE","HIDDEN").contains(status))throw new DomainException(400,"INVALID_REQUEST","Durum filtresini kontrol et.");return status;}
 private String kind(String kind){if(!Set.of("QUESTION","COMMUNITY","ADMIN").contains(kind))throw new DomainException(400,"INVALID_REQUEST","İçerik türünü kontrol et.");return kind;}
 private void version(long actual,long requested){if(actual!=requested)throw new DomainException(409,"STALE_VERSION","Kayıt değişmiş. Güncel listeyi yükle.");}
 private String reason(ManagementStatusRequest request){if(request.hidden()==null||request.reason()==null||request.reason().isBlank()||request.reason().strip().length()>1000||request.version()<0)throw new DomainException(400,"REASON_REQUIRED","Gerekçe yaz (en fazla 1000 karakter).");return request.reason().strip();}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public PageResponse<ManagedUserResponse> users(UUID actor,String q,String status,String authority,int page,int size){reader(actor);SearchQuery.page(page,size);String query=SearchQuery.clean(q);status(status);if(!Set.of("","MEMBER","ADMIN","MANAGER").contains(authority))throw new DomainException(400,"INVALID_REQUEST","Yetki filtresini kontrol et.");return new PageResponse<>(repository.users(query,status,authority,page,size).stream().map(mapper::user).toList(),page,size,repository.userCount(query,status,authority));}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public PageResponse<ManagedContentResponse> contents(UUID actor,String kind,String q,String status,int page,int size){reader(actor);SearchQuery.page(page,size);kind(kind);status(status);String query=SearchQuery.clean(q);return new PageResponse<>(repository.contents(kind,query,status,page,size).stream().map(mapper::content).toList(),page,size,repository.contentCount(kind,query,status));}
 @Transactional(readOnly=true)
 public ManagementStatsResponse stats(UUID actor){reader(actor);return mapper.stats(repository.stats());}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public PageResponse<ManagementActionResponse> actions(UUID actor,int page,int size){reader(actor);SearchQuery.page(page,size);return new PageResponse<>(repository.actions(page,size).stream().map(mapper::action).toList(),page,size,repository.actionCount());}
 @Transactional
 public ManagedUserResponse userStatus(UUID actor,UUID id,ManagementStatusRequest request){
  String reason=reason(request);
  // Reject Manager targets before taking either account lock, matching application lock ordering.
  if(repository.user(id).orElseThrow(this::missing).authority().equals("MANAGER"))throw denied();
  manager(actor);var a=accounts.lockForManagement(id);if(a.getAuthority()==Authority.MANAGER)throw denied();version(a.getVersion(),request.version());
  if(a.isDeleted()!=request.hidden()){
   if(request.hidden())applications.rejectPendingForDeactivation(actor,id,reason);
   lifecycle.setDisabled(a,request.hidden(),clock.instant());repository.audit(actor,request.hidden()?"DISABLE_USER":"RESTORE_USER","USER",id,reason);
  }
  return mapper.user(repository.user(id).orElseThrow(this::missing));
 }
 private ManagedContent find(UUID id,String kind){return repository.content(id,kind).orElseThrow(this::missing);}
 @Transactional
 public ManagedContentResponse contentStatus(UUID actor,UUID id,String kind,ManagementStatusRequest request){
  kind(kind);String reason=reason(request);var before=find(id,kind);
  // Same question -> actor order as owner answer/question writes. Parent lock serializes all answer mutations.
  if(!repository.lockQuestion(before.questionId()))throw missing();manager(actor);var a=find(id,kind);version(a.version(),request.version());
  if((a.moderatedAt()!=null)!=request.hidden()){
   repository.moderate(id,kind,request.hidden());repository.audit(actor,request.hidden()?"HIDE_CONTENT":"RESTORE_CONTENT",kind,id,reason);
  }
  return mapper.content(find(id,kind));
 }
}
