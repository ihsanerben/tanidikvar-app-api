package com.tanidikvar.api.answer.service;
import com.tanidikvar.api.answer.dto.*;
import com.tanidikvar.api.answer.entity.Answer;
import com.tanidikvar.api.answer.mapper.AnswerMapper;
import com.tanidikvar.api.answer.repository.AnswerRepository;
import com.tanidikvar.api.auth.service.AccountAccessService;
import com.tanidikvar.api.common.dto.PageResponse;
import com.tanidikvar.api.common.error.DomainException;
import com.tanidikvar.api.profile.service.InteractionPolicy;
import com.tanidikvar.api.question.dto.QuestionState;
import com.tanidikvar.api.question.service.QuestionAccessService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AnswerService {
    private final AnswerRepository answers;
    private final AnswerMapper mapper;
    private final AccountAccessService accounts;
    private final InteractionPolicy interaction;
    private final QuestionAccessService questions;
    public AnswerService(AnswerRepository answers,AnswerMapper mapper,AccountAccessService accounts,InteractionPolicy interaction,QuestionAccessService questions) {
        this.answers=answers;this.mapper=mapper;this.accounts=accounts;this.interaction=interaction;this.questions=questions;
    }
    private Answer find(UUID id) { return answers.find(id).orElseThrow(()->new DomainException(404,"NOT_FOUND","Cevap bulunamadı.")); }
    private void active(QuestionState q) {if(q.archivedAt()!=null)throw new DomainException(409,"QUESTION_ARCHIVED","Arşivlenmiş soru yeni cevap veya düzenlemeye kapalıdır.");}
    private void actor(UUID actor) {accounts.lockActive(actor);interaction.requireCompleted(actor);}
    private void owner(Answer a,UUID actor) {if(!a.authorId().equals(actor))throw new DomainException(403,"ACCESS_DENIED","Yalnız kendi cevabını yönetebilirsin.");}
    private void version(Answer a,long version) {if(a.version()!=version)throw new DomainException(409,"STALE_VERSION","Cevap değişmiş. Güncel cevabı yükle.");}
    private String body(String value) {
        String clean=value.replaceAll("(?U)^\\s+|\\s+$","");
        if(clean.length()<10||clean.length()>5000)throw new DomainException(400,"VALIDATION_FAILED","Cevabını kontrol et.",Map.of("body","10–5000 karakter"));
        return clean;
    }
    @Transactional(readOnly=true)
    public PageResponse<AnswerResponse> list(UUID question,int page,int size) {
        if(page<0||page>10000||size<1||size>100)throw new DomainException(400,"INVALID_REQUEST","Sayfa sınırlarını kontrol et.");
        questions.requireReadable(question);
        return new PageResponse<>(answers.list(question,page,size).stream().map(mapper::toResponse).toList(),page,size,answers.count(question));
    }
    @Transactional(readOnly=true)
    public Optional<AnswerResponse> mine(UUID question,UUID actor) {
        questions.requireReadable(question);return answers.own(question,actor).map(mapper::toResponse);
    }
    @Transactional
    public AnswerResponse create(UUID question,UUID actor,AnswerCreateRequest request) {
        var q=questions.lock(question);actor(actor);String text=body(request.body());
        var existing=answers.own(question,actor);
        if(existing.isPresent()) {
            var a=existing.get();
            if(a.deletedAt()!=null)throw new DomainException(409,"ANSWER_REMOVED","Kaldırdığın cevabı geri yükleyebilirsin.");
            if(!a.body().equals(text))throw new DomainException(409,"ANSWER_EXISTS","Bu soruya zaten cevap verdin. Mevcut cevabını düzenle.");
            return mapper.toResponse(a);
        }
        active(q);UUID id=UUID.randomUUID();answers.create(id,question,actor,text);return mapper.toResponse(find(id));
    }
    @Transactional
    public AnswerResponse update(UUID id,UUID actor,AnswerUpdateRequest request) {
        var before=find(id);var q=questions.lock(before.questionId());actor(actor);var a=find(id);owner(a,actor);version(a,request.version());active(q);
        if(a.deletedAt()!=null)throw new DomainException(409,"ANSWER_REMOVED","Önce cevabını geri yükle.");
        String text=body(request.body());if(!text.equals(a.body()))answers.update(id,text);return mapper.toResponse(find(id));
    }
    @Transactional
    public AnswerResponse status(UUID id,UUID actor,AnswerStatusRequest request) {
        var before=find(id);var q=questions.lock(before.questionId());actor(actor);var a=find(id);owner(a,actor);version(a,request.version());
        if(!request.deleted())active(q);
        if((a.deletedAt()!=null)!=request.deleted())answers.status(id,request.deleted());
        return mapper.toResponse(find(id));
    }
}
