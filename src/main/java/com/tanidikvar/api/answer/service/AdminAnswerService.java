package com.tanidikvar.api.answer.service;
import com.tanidikvar.api.answer.repository.AdminAnswerRepository;
import com.tanidikvar.api.answer.mapper.AdminAnswerMapper;
import com.tanidikvar.api.answer.entity.AdminAnswer;
import com.tanidikvar.api.answer.dto.*;
import com.tanidikvar.api.auth.entity.*;
import com.tanidikvar.api.auth.service.AccountAccessService;
import com.tanidikvar.api.application.service.VerificationAccessService;
import com.tanidikvar.api.profile.service.*;
import com.tanidikvar.api.question.service.QuestionAccessService;
import com.tanidikvar.api.question.dto.QuestionState;
import com.tanidikvar.api.common.dto.PageResponse;
import com.tanidikvar.api.common.error.DomainException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AdminAnswerService {
 private static final ZoneId ZONE=ZoneId.of("Europe/Istanbul");
 private static final int LIMIT=5;
 private final AdminAnswerRepository answers;private final AdminAnswerMapper mapper;private final AccountAccessService accounts;private final VerificationAccessService verifications;private final InteractionPolicy interaction;private final ProfileSummaryService profiles;private final QuestionAccessService questions;private final Clock clock;
 public AdminAnswerService(AdminAnswerRepository answers,AdminAnswerMapper mapper,AccountAccessService accounts,VerificationAccessService verifications,InteractionPolicy interaction,ProfileSummaryService profiles,QuestionAccessService questions,Clock clock){this.answers=answers;this.mapper=mapper;this.accounts=accounts;this.verifications=verifications;this.interaction=interaction;this.profiles=profiles;this.questions=questions;this.clock=clock;}
 private void page(int page,int size){if(page<0||page>10000||size<1||size>100)throw new DomainException(400,"INVALID_REQUEST","Sayfa sınırlarını kontrol et.");}
 private DomainException denied(){return new DomainException(403,"ADMIN_REQUIRED","Güncel Admin yetkisi ve doğrulaması gerekiyor.");}
 private boolean activeAdmin(Account a){return a.getAuthority()==Authority.ADMIN&&profiles.status(a.getId())!=null&&verifications.approved(a.getId(),a.getActiveVerificationApplicationId());}
 private UUID requireAdmin(Account a){if(a.getAuthority()!=Authority.ADMIN)throw denied();return verifications.requireApproved(a.getId(),a.getActiveVerificationApplicationId());}
 private Account actor(UUID id){var a=accounts.lockActive(id);interaction.requireCompleted(id);return a;}
 private void active(QuestionState q){if(q.archivedAt()!=null)throw new DomainException(409,"QUESTION_ARCHIVED","Arşivde yeni cevap, düzenleme veya geri yükleme yapılamaz.");}
 private void version(long actual,long requested){if(actual!=requested)throw new DomainException(409,"STALE_VERSION","Kayıt değişmiş. Güncel bilgileri yükle.");}
 private void unmoderated(AdminAnswer a){if(a.moderatedAt()!=null)throw new DomainException(409,"ANSWER_MODERATED","Bu cevap Manager tarafından gizlendi. Düzenlenemez veya geri yüklenemez.");}
 private AdminAnswer find(UUID id){return answers.find(id).orElseThrow(()->new DomainException(404,"NOT_FOUND","Cevap bulunamadı."));}
 private void owner(AdminAnswer a,UUID id){if(!a.authorId().equals(id))throw new DomainException(403,"ACCESS_DENIED","Yalnız kendi cevabını yönetebilirsin.");}
 private String text(String body){String s=body.replaceAll("(?U)^\\s+|\\s+$","");if(s.length()<10||s.length()>5000)throw new DomainException(400,"VALIDATION_FAILED","Cevabını kontrol et.",Map.of("body","10–5000 karakter"));return s;}
 private void assigned(UUID q,UUID id){if(!answers.assignment(q,id).assigned())throw new DomainException(409,"ASSIGNMENT_REQUIRED","Önce bu soruya cevaplayacağım diyerek atan.");}
 @Transactional(readOnly=true)
 public PageResponse<AdminAnswerResponse> list(UUID q,int page,int size){questions.requireReadable(q);return listRows(q,null,false,page,size);}
 @Transactional(readOnly=true)
 public PageResponse<AdminAnswerResponse> history(UUID author,int page,int size){return listRows(null,author,false,page,size);}
 @Transactional(readOnly=true)
 public PageResponse<AdminAnswerResponse> mine(UUID author,int page,int size){return listRows(null,author,true,page,size);}
 private PageResponse<AdminAnswerResponse> listRows(UUID q,UUID author,boolean removed,int page,int size){page(page,size);return new PageResponse<>(answers.list(q,author,removed,page,size).stream().map(mapper::toResponse).toList(),page,size,answers.count(q,author,removed));}
 @Transactional(readOnly=true)
 public OwnAdminAnswerResponse own(UUID q,UUID actor){questions.requireReadable(q);return new OwnAdminAnswerResponse(answers.own(q,actor).map(mapper::toResponse).orElse(null),answers.assignment(q,actor));}
 @Transactional
 public AssignmentResponse assign(UUID q,UUID id,AssignmentRequest request){var question=questions.lock(q);var account=actor(id);var current=answers.assignment(q,id);version(current.version(),request.version());
  if(request.assigned()){active(question);requireAdmin(account);}
  if(current.assigned()!=request.assigned())answers.assign(q,id,request.assigned(),current.version());return answers.assignment(q,id);
 }
 @Transactional(readOnly=true)
 public PageResponse<AssignmentResponse> assignments(UUID actor,int page,int size){page(page,size);return new PageResponse<>(answers.assignments(actor,page,size),page,size,answers.assignmentCount(actor));}
 private AdminQuotaResponse quota(Account actor,Instant now){LocalDate day=now.atZone(ZONE).toLocalDate();Instant start=day.atStartOfDay(ZONE).toInstant(),end=day.plusDays(1).atStartOfDay(ZONE).toInstant();long used=answers.daily(actor.getId(),start,end);return new AdminQuotaResponse(activeAdmin(actor),day,used,LIMIT,Math.max(0,LIMIT-used),end);}
 @Transactional
 public AdminQuotaResponse quota(UUID actor){return quota(accounts.lockActive(actor),clock.instant());}
 @Transactional
 public AdminAnswerResponse create(UUID q,UUID id,AnswerCreateRequest request){var question=questions.lock(q);var account=actor(id);UUID verification=requireAdmin(account);String body=text(request.body());var old=answers.own(q,id);
  if(old.isPresent()){var a=old.get();unmoderated(a);if(a.deletedAt()!=null)throw new DomainException(409,"ANSWER_REMOVED","Cevabını geri yükleyebilirsin.");if(!a.body().equals(body))throw new DomainException(409,"ANSWER_EXISTS","Mevcut cevabını düzenle.");return mapper.toResponse(a);}
  active(question);assigned(q,id);Instant now=clock.instant();if(quota(account,now).remaining()==0)throw new DomainException(409,"DAILY_LIMIT","Bugünkü beş farklı soru hakkını kullandın.");
  UUID answer=UUID.randomUUID();answers.create(answer,q,id,verification,body,now);return mapper.toResponse(find(answer));
 }
 @Transactional
 public AdminAnswerResponse update(UUID id,UUID actor,AnswerUpdateRequest request){var before=find(id);var q=questions.lock(before.questionId());var account=actor(actor);var a=find(id);owner(a,actor);version(a.version(),request.version());requireAdmin(account);active(q);unmoderated(a);if(a.deletedAt()!=null)throw new DomainException(409,"ANSWER_REMOVED","Önce cevabını geri yükle.");String body=text(request.body());if(!body.equals(a.body()))answers.update(id,body);return mapper.toResponse(find(id));}
 @Transactional
 public AdminAnswerResponse status(UUID id,UUID actor,AnswerStatusRequest request){var before=find(id);var q=questions.lock(before.questionId());var account=actor(actor);var a=find(id);owner(a,actor);version(a.version(),request.version());
  if(!request.deleted()){unmoderated(a);requireAdmin(account);active(q);assigned(q.id(),actor);}
  if((a.deletedAt()!=null)!=request.deleted())answers.status(id,request.deleted());return mapper.toResponse(find(id));
 }
}

