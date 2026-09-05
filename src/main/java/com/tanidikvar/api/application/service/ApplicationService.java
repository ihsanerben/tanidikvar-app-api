package com.tanidikvar.api.application.service;
import com.tanidikvar.api.application.repository.ApplicationRepository;
import com.tanidikvar.api.application.dto.*;
import com.tanidikvar.api.application.mapper.ApplicationMapper;
import com.tanidikvar.api.application.entity.AdminApplication;
import com.tanidikvar.api.auth.entity.*;
import com.tanidikvar.api.auth.service.AccountAccessService;
import com.tanidikvar.api.catalog.service.CatalogService;
import com.tanidikvar.api.profile.service.ProfileService;
import com.tanidikvar.api.profile.entity.EducationStatus;
import com.tanidikvar.api.file.service.FileService;
import com.tanidikvar.api.common.error.DomainException;
import com.tanidikvar.api.common.dto.PageResponse;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ApplicationService {
 private final ApplicationRepository applications;private final ApplicationMapper mapper;private final AccountAccessService accounts;private final ProfileService profiles;private final CatalogService catalog;private final FileService files;private final Clock clock;
 public ApplicationService(ApplicationRepository applications,ApplicationMapper mapper,AccountAccessService accounts,ProfileService profiles,CatalogService catalog,FileService files,Clock clock){this.applications=applications;this.mapper=mapper;this.accounts=accounts;this.profiles=profiles;this.catalog=catalog;this.files=files;this.clock=clock;}
 @Transactional
 public Optional<ApplicationResponse> existing(UUID owner,ApplicationSubmission request,String hash){
  accounts.lockActive(owner);return duplicate(owner,request,hash);
 }
 private Optional<ApplicationResponse> duplicate(UUID owner,ApplicationSubmission request,String hash){
  return applications.request(owner,request.requestId()).map(a->{if(a.profileVersion()!=request.profileVersion()||!a.documentSha256().equals(hash))throw new DomainException(409,"REQUEST_CONFLICT","Bu gönderim farklı bilgilerle kullanılmış.");return mapper.toResponse(a);});
 }
 @Transactional
 public ApplicationResponse submit(UUID owner,ApplicationSubmission request,UUID file,String hash){
  var account=accounts.lockActive(owner);
  var old=duplicate(owner,request,hash);if(old.isPresent())return old.get();
  var p=profiles.get(owner);
  if(account.getAuthority()==Authority.MANAGER||!p.completed()||p.educationStatus()==EducationStatus.YKS_ADAYI)throw new DomainException(403,"APPLICATION_INELIGIBLE","Yalnız üniversite öğrencileri ve mezunlar başvurabilir.");
  if(p.version()!=request.profileVersion())throw new DomainException(409,"STALE_VERSION","Profil değişmiş. Bilgilerini tekrar kontrol et.");
  if(applications.pending(owner))throw new DomainException(409,"APPLICATION_PENDING","Zaten bekleyen bir başvurun var.");
  catalog.lockEducation(p.education().id(),true);
  UUID id=UUID.randomUUID();files.ready(file);applications.insert(id,owner,request.requestId(),p,file,hash);
  return mapper.toResponse(find(id));
 }
 @Transactional(readOnly=true)
 public PageResponse<ApplicationResponse> mine(UUID owner,int page,int size){return list(owner,null,page,size);}
 @Transactional
 public PageResponse<ApplicationResponse> manage(UUID actor,String status,int page,int size){manager(actor);return list(null,status,page,size);}
 private PageResponse<ApplicationResponse> list(UUID owner,String status,int page,int size){
  if(page<0||page>10000||size<1||size>100||status!=null&&!Set.of("PENDING","APPROVED","REJECTED").contains(status))throw new DomainException(400,"INVALID_REQUEST","Filtre ve sayfa sınırlarını kontrol et.");
  return new PageResponse<>(applications.list(owner,status,page,size).stream().map(mapper::toResponse).toList(),page,size,applications.count(owner,status));
 }
 // Managers cannot apply, so manager -> applicant is a consistent account lock order.
 @Transactional
 public ApplicationResponse decide(UUID actor,UUID id,ApplicationDecision request){
  manager(actor);var first=find(id);if(actor.equals(first.applicantId()))throw denied();
  var account=accounts.lockActive(first.applicantId());var a=find(id);
  if(account.getAuthority()==Authority.MANAGER)throw denied();
  if(a.version()!=request.version()||!a.status().equals("PENDING"))throw new DomainException(409,"STALE_VERSION","Başvuru kararı değişmiş. Listeyi yenile.");
  String reason=request.status().equals("REJECTED")?reason(request.reason()):null;
  if(request.status().equals("APPROVED"))files.requireVerification(a.documentFileId(),a.applicantId());
  applications.decide(id,actor,request.status(),reason);
  if(request.status().equals("APPROVED"))account.grantAdmin(id,clock.instant());
  applications.audit(actor,request.status(),"ADMIN_APPLICATION",id,reason);
  // JPA flush occurs at commit; derive this response's flag from the decision.
  var result=mapper.toResponse(find(id));
  return new ApplicationResponse(result.id(),result.applicantId(),result.firstName(),result.lastName(),result.educationStatus(),result.universityName(),result.departmentName(),result.graduationYear(),result.occupation(),result.company(),result.documentFileId(),result.status(),result.submittedAt(),result.reviewedBy(),result.reviewedAt(),result.rejectionReason(),result.version(),request.status().equals("APPROVED"));
 }
 @Transactional
 public void revoke(UUID actor,UUID owner,RevokeRequest request){
  manager(actor);if(actor.equals(owner))throw denied();var account=accounts.lockActive(owner);
  if(account.getAuthority()!=Authority.ADMIN||!Objects.equals(account.getActiveVerificationApplicationId(),request.verificationId()))throw new DomainException(409,"STALE_VERSION","Aktif doğrulama değişmiş. Listeyi yenile.");
  String reason=reason(request.reason());
  for(UUID id:applications.pendingIds(owner)){applications.decide(id,actor,"REJECTED","Admin yetkisi kaldırıldığı için kapatıldı: "+reason.substring(0,Math.min(reason.length(),900)));applications.audit(actor,"REJECTED","ADMIN_APPLICATION",id,"Yetki kaldırılması nedeniyle kapatıldı.");}
  account.revokeAdmin(clock.instant());applications.audit(actor,"REVOKE_ADMIN","USER",owner,reason);
 }
 private String reason(String reason){if(reason==null||reason.isBlank()||reason.strip().length()>1000)throw new DomainException(400,"REASON_REQUIRED","Gerekçe yaz (en fazla 1000 karakter).");return reason.strip();}
 private void manager(UUID actor){if(accounts.lockActive(actor).getAuthority()!=Authority.MANAGER)throw denied();}
 private DomainException denied(){return new DomainException(403,"ACCESS_DENIED","Manager yetkisi gerekir; Manager hesapları bu işleme dahil edilemez.");}
 private AdminApplication find(UUID id){return applications.find(id).orElseThrow(()->new DomainException(404,"NOT_FOUND","Başvuru bulunamadı."));}
}
