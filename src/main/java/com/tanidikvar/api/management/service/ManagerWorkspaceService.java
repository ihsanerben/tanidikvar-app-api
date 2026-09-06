package com.tanidikvar.api.management.service;

import com.tanidikvar.api.auth.entity.Authority;
import com.tanidikvar.api.auth.service.AccountAccessService;
import com.tanidikvar.api.catalog.entity.CatalogKind;
import com.tanidikvar.api.catalog.service.CatalogService;
import com.tanidikvar.api.common.dto.*;
import com.tanidikvar.api.common.error.DomainException;
import com.tanidikvar.api.management.dto.*;
import com.tanidikvar.api.management.entity.*;
import com.tanidikvar.api.management.mapper.ManagementMapper;
import com.tanidikvar.api.management.repository.ManagementRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ManagerWorkspaceService {
 private final ManagementRepository repository;
 private final ManagementMapper mapper;
 private final AccountAccessService accounts;
 private final CatalogService catalog;
 public ManagerWorkspaceService(ManagementRepository repository,ManagementMapper mapper,AccountAccessService accounts,CatalogService catalog){this.repository=repository;this.mapper=mapper;this.accounts=accounts;this.catalog=catalog;}
 private DomainException missing(){return new DomainException(404,"NOT_FOUND","Kayıt bulunamadı.");}
 private void reader(UUID actor){var u=repository.user(actor).orElseThrow(this::missing);if(u.deletedAt()!=null||!u.authority().equals("MANAGER")||!u.emailVerified())throw new DomainException(403,"ACCESS_DENIED","Manager yetkisi gerekiyor.");}
 private void manager(UUID actor){if(accounts.lockActive(actor).getAuthority()!=Authority.MANAGER)throw new DomainException(403,"ACCESS_DENIED","Manager yetkisi gerekiyor.");}
 private void version(long actual,long expected){if(actual!=expected)throw new DomainException(409,"STALE_VERSION","Kayıt değişmiş. Güncel bilgileri yükle.");}
 private ManagerAccountResponse accountResponse(ManagerIdentity a){return new ManagerAccountResponse(a.firstName(),a.lastName(),a.email(),a.version());}
 @Transactional(readOnly=true)
 public ManagerAccountResponse account(UUID actor){reader(actor);return accountResponse(repository.identity(actor));}
 @Transactional
 public ManagerAccountResponse saveAccount(UUID actor,ManagerAccountRequest request){manager(actor);var old=repository.identity(actor);version(old.version(),request.version());String first=request.firstName().replaceAll("[\\s\\p{Z}]+"," ").strip(),last=request.lastName().replaceAll("[\\s\\p{Z}]+"," ").strip();if(first.isBlank()||last.isBlank())throw new DomainException(400,"VALIDATION_FAILED","Ad ve soyad gerekiyor.");if(!Objects.equals(old.firstName(),first)||!Objects.equals(old.lastName(),last))repository.saveIdentity(actor,first,last);return accountResponse(repository.identity(actor));}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public ManagedUserDetailResponse user(UUID actor,UUID id){reader(actor);var d=repository.detail(id).orElseThrow(this::missing);return new ManagedUserDetailResponse(mapper.user(d.user()),d.universityName(),d.departmentName(),d.graduationYear(),d.verificationId(),d.questions(),d.communityAnswers(),d.adminAnswers());}
 private ClassificationResponse classification(ManagementClassification c){return new ClassificationResponse(c.scope(),c.universityId(),c.universityDepartmentId(),c.tagIds(),c.version(),c.universityDepartmentId()==null?null:catalog.education(c.universityDepartmentId()));}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public ManagedQuestionResponse question(UUID actor,UUID id,int page,int size){reader(actor);SearchQuery.page(page,size);var q=repository.content(id,"QUESTION").orElseThrow(this::missing);return new ManagedQuestionResponse(mapper.content(q),classification(repository.classification(id)),new PageResponse<>(repository.questionAnswers(id,page,size).stream().map(mapper::content).toList(),page,size,repository.questionAnswerCount(id)));}
 @Transactional
 public ClassificationResponse classify(UUID actor,UUID id,ClassificationRequest request){
  if(!repository.lockQuestion(id))throw missing();manager(actor);var old=repository.classification(id);version(old.version(),request.version());
  boolean valid=switch(request.scope()){case GENERAL->request.universityId()==null&&request.universityDepartmentId()==null;case UNIVERSITY->request.universityId()!=null&&request.universityDepartmentId()==null;case UNIVERSITY_DEPARTMENT->request.universityId()==null&&request.universityDepartmentId()!=null;};
  if(!valid||new HashSet<>(request.tagIds()).size()!=request.tagIds().size())throw new DomainException(400,"VALIDATION_FAILED","Kapsam ve tag seçimini kontrol et.");
  if(request.universityId()!=null)catalog.lockReference(CatalogKind.UNIVERSITY,request.universityId(),!request.universityId().equals(old.universityId()));
  if(request.universityDepartmentId()!=null)catalog.lockEducation(request.universityDepartmentId(),!request.universityDepartmentId().equals(old.universityDepartmentId()));
  for(UUID tag:request.tagIds().stream().sorted().toList())catalog.lockReference(CatalogKind.TAG,tag,!old.tagIds().contains(tag));
  if(old.scope()!=request.scope()||!Objects.equals(old.universityId(),request.universityId())||!Objects.equals(old.universityDepartmentId(),request.universityDepartmentId())||!new HashSet<>(old.tagIds()).equals(new HashSet<>(request.tagIds()))){
   repository.classify(id,new ManagementClassification(request.scope(),request.universityId(),request.universityDepartmentId(),request.tagIds(),old.version()));
   repository.audit(actor,"CLASSIFY_QUESTION","QUESTION",id,request.reason().strip());
  }
  return classification(repository.classification(id));
 }
 @Transactional(readOnly=true)
 public CatalogUsageResponse usage(UUID actor,String kind,UUID id){reader(actor);if(!Set.of("UNIVERSITY","DEPARTMENT","UNIVERSITY_DEPARTMENT","TAG").contains(kind))throw new DomainException(400,"INVALID_REQUEST","Katalog türünü kontrol et.");var u=repository.usage(kind,id);return new CatalogUsageResponse(u.profiles(),u.questions());}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public PageResponse<ManagementActionResponse> actions(UUID actor,String q,String action,String type,int page,int size){reader(actor);SearchQuery.page(page,size);q=SearchQuery.clean(q);action=SearchQuery.clean(action);type=SearchQuery.clean(type);return new PageResponse<>(repository.filteredActions(q,action,type,page,size).stream().map(mapper::action).toList(),page,size,repository.filteredActionCount(q,action,type));}
 @Transactional(readOnly=true)
 public ActionDetailResponse action(UUID actor,UUID id){reader(actor);var a=repository.action(id).orElseThrow(this::missing);return new ActionDetailResponse(mapper.action(a),repository.actorName(a.actorId()));}
}
