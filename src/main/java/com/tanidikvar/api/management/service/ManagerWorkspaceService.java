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
 public ManagedUserDetailResponse user(UUID actor,UUID id){reader(actor);var d=repository.detail(id).orElseThrow(this::missing);return new ManagedUserDetailResponse(mapper.user(d.user()),d.firstName(),d.lastName(),d.universityId(),d.universityName(),d.departmentId(),d.departmentName(),d.graduationYear(),d.avatarFileId(),d.biography(),d.occupation(),d.company(),d.linkedinUrl(),d.portfolioUrl(),d.verificationId(),d.questions(),d.communityAnswers(),d.adminAnswers());}
 @Transactional public ManagedUserDetailResponse editUser(UUID actor,UUID id,ManagedUserEditRequest request){manager(actor);var target=repository.user(id).orElseThrow(this::missing);if(target.authority().equals("MANAGER"))throw new DomainException(403,"ACCESS_DENIED","Manager profili bu ekrandan değiştirilemez.");var fields=new LinkedHashMap<String,String>();String first=request.firstName().strip(),last=request.lastName().strip();if(request.educationStatus()==com.tanidikvar.api.profile.entity.EducationStatus.YKS_ADAYI){if(request.universityId()!=null||request.departmentId()!=null)fields.put("educationStatus","YKS adayı için üniversite ve bölüm seçilmez.");}else{if(request.universityId()==null)fields.put("universityId","Üniversite seç.");if(request.departmentId()==null)fields.put("departmentId","Bölüm seç.");}int year=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Istanbul")).getYear();if(request.educationStatus()==com.tanidikvar.api.profile.entity.EducationStatus.MEZUN){if(request.graduationYear()==null||request.graduationYear()<1900||request.graduationYear()>year)fields.put("graduationYear","Geçerli bir mezuniyet yılı yaz.");}else if(request.graduationYear()!=null)fields.put("graduationYear","Mezuniyet yılı yalnız mezunlar içindir.");if(!fields.isEmpty())throw new DomainException(400,"VALIDATION_FAILED","Kullanıcı bilgilerini kontrol et.",fields);if(request.universityId()!=null)catalog.lockReference(CatalogKind.UNIVERSITY,request.universityId(),!Objects.equals(request.universityId(),repository.detail(id).orElseThrow(this::missing).universityId()));if(request.departmentId()!=null)catalog.lockReference(CatalogKind.DEPARTMENT,request.departmentId(),!Objects.equals(request.departmentId(),repository.detail(id).orElseThrow(this::missing).departmentId()));repository.editUser(id,first,last,request.educationStatus().name(),request.universityId(),request.departmentId(),request.graduationYear(),clean(request.biography()),clean(request.occupation()),clean(request.company()),clean(request.linkedinUrl()),clean(request.portfolioUrl()));repository.audit(actor,"EDIT_USER","USER",id,"Kullanıcı profil bilgileri güncellendi.");return user(actor,id);}
 private String clean(String value){return value==null||value.isBlank()?null:value.strip();}
 private ClassificationResponse classification(ManagementClassification c){return new ClassificationResponse(c.scope(),c.universityId(),c.departmentId(),c.tagIds(),c.version(),c.departmentId()==null?null:catalog.selection(c.universityId(),c.departmentId()));}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public ManagedQuestionResponse question(UUID actor,UUID id,int page,int size){reader(actor);SearchQuery.page(page,size);var q=repository.content(id,"QUESTION").orElseThrow(this::missing);return new ManagedQuestionResponse(mapper.content(q),classification(repository.classification(id)),new PageResponse<>(repository.questionAnswers(id,page,size).stream().map(mapper::content).toList(),page,size,repository.questionAnswerCount(id)));}
 @Transactional
 public ClassificationResponse classify(UUID actor,UUID id,ClassificationRequest request){
  if(!repository.lockQuestion(id))throw missing();manager(actor);var old=repository.classification(id);version(old.version(),request.version());
  var content=repository.content(id,"QUESTION").orElseThrow(this::missing);
  String title=request.title()==null?content.title():request.title().replaceAll("(?U)\\s+"," ").strip();
  if(title.length()<10||title.length()>200)throw new DomainException(400,"VALIDATION_FAILED","Soru başlığı 10–200 karakter olmalı.",Map.of("title","10–200 karakter"));
  String body=request.body()==null?content.body():request.body().strip();
  if(body!=null&&body.isEmpty())body=null;
  boolean textChanged=!Objects.equals(content.title(),title)||!Objects.equals(content.body(),body);
  boolean valid=switch(request.scope()){case GENERAL->request.universityId()==null&&request.departmentId()==null;case UNIVERSITY->request.universityId()!=null&&request.departmentId()==null;case UNIVERSITY_DEPARTMENT->request.universityId()!=null&&request.departmentId()!=null;};
  if(!valid||new HashSet<>(request.tagIds()).size()!=request.tagIds().size())throw new DomainException(400,"VALIDATION_FAILED","Kapsam ve tag seçimini kontrol et.");
  if(request.universityId()!=null)catalog.lockReference(CatalogKind.UNIVERSITY,request.universityId(),!request.universityId().equals(old.universityId()));
  if(request.departmentId()!=null)catalog.lockReference(com.tanidikvar.api.catalog.entity.CatalogKind.DEPARTMENT,request.departmentId(),!request.departmentId().equals(old.departmentId()));
  for(UUID tag:request.tagIds().stream().sorted().toList())catalog.lockReference(CatalogKind.TAG,tag,!old.tagIds().contains(tag));
  if(textChanged||old.scope()!=request.scope()||!Objects.equals(old.universityId(),request.universityId())||!Objects.equals(old.departmentId(),request.departmentId())||!new HashSet<>(old.tagIds()).equals(new HashSet<>(request.tagIds()))){
   repository.classify(id,new ManagementClassification(request.scope(),request.universityId(),request.departmentId(),request.tagIds(),old.version()),title,body,textChanged);
   repository.audit(actor,textChanged?"EDIT_QUESTION":"CLASSIFY_QUESTION","QUESTION",id,request.reason().strip());
  }
  return classification(repository.classification(id));
 }
 @Transactional(readOnly=true)
 public CatalogUsageResponse usage(UUID actor,String kind,UUID id){reader(actor);if(!Set.of("UNIVERSITY","DEPARTMENT","TAG").contains(kind))throw new DomainException(400,"INVALID_REQUEST","Katalog türünü kontrol et.");var u=repository.usage(kind,id);return new CatalogUsageResponse(u.profiles(),u.questions());}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public PageResponse<ManagementActionResponse> actions(UUID actor,String q,String action,String type,java.time.LocalDate dateFrom,java.time.LocalDate dateTo,int page,int size){reader(actor);SearchQuery.page(page,size);q=SearchQuery.clean(q);action=SearchQuery.clean(action);type=SearchQuery.clean(type);if(dateFrom!=null&&dateTo!=null&&dateFrom.isAfter(dateTo))throw new DomainException(400,"INVALID_DATE_RANGE","Başlangıç tarihi bitiş tarihinden sonra olamaz.");var zone=java.time.ZoneId.of("Europe/Istanbul");var from=dateFrom==null?null:dateFrom.atStartOfDay(zone).toInstant();var until=dateTo==null?null:dateTo.plusDays(1).atStartOfDay(zone).toInstant();return new PageResponse<>(repository.filteredActions(q,action,type,from,until,page,size).stream().map(mapper::action).toList(),page,size,repository.filteredActionCount(q,action,type,from,until));}
 @Transactional(readOnly=true)
 public ActionDetailResponse action(UUID actor,UUID id){reader(actor);var a=repository.action(id).orElseThrow(this::missing);return new ActionDetailResponse(mapper.action(a),repository.actorName(a.actorId()));}
}
