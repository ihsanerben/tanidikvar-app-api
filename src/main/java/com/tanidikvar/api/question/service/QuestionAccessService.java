package com.tanidikvar.api.question.service;
import com.tanidikvar.api.question.dto.QuestionState;
import com.tanidikvar.api.question.repository.QuestionRepository;
import com.tanidikvar.api.common.error.DomainException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class QuestionAccessService {
    private final QuestionRepository questions;
    public QuestionAccessService(QuestionRepository questions) { this.questions=questions; }
    private QuestionState state(UUID id,boolean lock) {
        var q=questions.find(id,lock).orElseThrow(()->new DomainException(404,"NOT_FOUND","Soru bulunamadı."));
        return new QuestionState(q.id(),q.archivedAt());
    }
    @Transactional(readOnly=true)
    public QuestionState requireReadable(UUID id) { return state(id,false); }
    @Transactional(propagation=Propagation.MANDATORY)
    public QuestionState lock(UUID id) { return state(id,true); }
}
