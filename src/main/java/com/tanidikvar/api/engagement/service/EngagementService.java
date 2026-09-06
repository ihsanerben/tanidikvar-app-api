package com.tanidikvar.api.engagement.service;
import com.tanidikvar.api.auth.service.AccountAccessService;
import com.tanidikvar.api.profile.service.InteractionPolicy;
import com.tanidikvar.api.question.service.QuestionAccessService;
import com.tanidikvar.api.engagement.dto.*;
import com.tanidikvar.api.engagement.mapper.EngagementMapper;
import com.tanidikvar.api.engagement.repository.EngagementRepository;
import com.tanidikvar.api.common.error.DomainException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class EngagementService {
    private final EngagementRepository repository;
    private final EngagementMapper mapper;
    private final QuestionAccessService questions;
    private final AccountAccessService accounts;
    private final InteractionPolicy interaction;
    public EngagementService(EngagementRepository repository,EngagementMapper mapper,QuestionAccessService questions,AccountAccessService accounts,InteractionPolicy interaction) {
        this.repository=repository;this.mapper=mapper;this.questions=questions;this.accounts=accounts;this.interaction=interaction;
    }
    @Transactional(readOnly=true)
    public LikeResponse mine(UUID id,UUID actor) {questions.requireReadable(id);return mapper.toResponse(repository.like(id,actor));}
    @Transactional
    public LikeResponse like(UUID id,UUID actor,LikeRequest request) {
        var question=questions.lock(id);accounts.lockActive(actor);interaction.requireCompleted(actor);
        var current=repository.like(id,actor);
        if(current.version()!=request.version())throw new DomainException(409,"STALE_VERSION","Beğeni durumu değişmiş. Güncel durumu yükle.");
        if(current.liked()==request.liked())return mapper.toResponse(current);
        if(request.liked()&&question.archivedAt()!=null)throw new DomainException(409,"QUESTION_ARCHIVED","Arşivlenmiş soru yeni beğeniye kapalıdır.");
        repository.setLike(id,actor,request.liked(),current.version());return mapper.toResponse(repository.like(id,actor));
    }
    @Transactional
    public void view(UUID id,ViewRequest request) { view(id,request,null); }
    @Transactional
    public void view(UUID id,ViewRequest request,UUID actor) {
        questions.lock(id);
        if(actor!=null && accounts.lockActive(actor).getAuthority()==com.tanidikvar.api.auth.entity.Authority.MANAGER)return;
        if(!repository.view(id,request.openingEventId()).equals(id))throw new DomainException(409,"REQUEST_CONFLICT","Bu açılış kimliği başka bir soruda kullanılmış.");
    }
}
