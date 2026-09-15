package com.tanidikvar.api.evaluation.service;
import com.tanidikvar.api.common.dto.*;import com.tanidikvar.api.common.error.DomainException;import com.tanidikvar.api.evaluation.dto.*;import com.tanidikvar.api.evaluation.repository.EvaluationRepository;import com.tanidikvar.api.profile.service.InteractionPolicy;import java.util.UUID;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;
@Service public class EvaluationService{
 private final EvaluationRepository repository;private final InteractionPolicy interaction;public EvaluationService(EvaluationRepository repository,InteractionPolicy interaction){this.repository=repository;this.interaction=interaction;}
 private void context(UUID university,UUID program){if(!repository.context(university,program))throw new DomainException(404,"NOT_FOUND","Değerlendirme bağlamı bulunamadı.");}
 @Transactional public EvaluationResponse save(UUID author,EvaluationRequest request){interaction.requireCompleted(author);context(request.universityId(),request.programId());return repository.save(author,request);}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public PageResponse<EvaluationResponse> list(UUID university,UUID program,int page,int size){SearchQuery.page(page,size);context(university,program);return new PageResponse<>(repository.list(university,program,page,size),page,size,repository.count(university,program));}
 @Transactional(readOnly=true) public EvaluationSummaryResponse summary(UUID university,UUID program){context(university,program);return repository.summary(university,program);}
}
