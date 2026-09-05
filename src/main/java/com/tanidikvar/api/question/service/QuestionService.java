package com.tanidikvar.api.question.service;
import com.tanidikvar.api.auth.service.AccountAccessService;
import com.tanidikvar.api.catalog.entity.CatalogKind;
import com.tanidikvar.api.catalog.service.CatalogService;
import com.tanidikvar.api.common.dto.PageResponse;
import com.tanidikvar.api.common.error.DomainException;
import com.tanidikvar.api.profile.service.InteractionPolicy;
import com.tanidikvar.api.question.dto.*;
import com.tanidikvar.api.question.entity.*;
import com.tanidikvar.api.question.mapper.QuestionMapper;
import com.tanidikvar.api.question.repository.QuestionRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class QuestionService {
    private final com.tanidikvar.api.engagement.service.QuestionStatisticsService statistics;
    private final QuestionRepository questions;
    private final QuestionMapper mapper;
    private final AccountAccessService accounts;
    private final InteractionPolicy interaction;
    private final CatalogService catalog;
    public QuestionService(QuestionRepository questions,QuestionMapper mapper,AccountAccessService accounts,InteractionPolicy interaction,CatalogService catalog,com.tanidikvar.api.engagement.service.QuestionStatisticsService statistics) {
        this.statistics=statistics;
        this.questions=questions;this.mapper=mapper;this.accounts=accounts;this.interaction=interaction;this.catalog=catalog;
    }
    private Question find(UUID id,boolean lock) { return questions.find(id,lock).orElseThrow(()->new DomainException(404,"NOT_FOUND","Soru bulunamadı.")); }
    private QuestionResponse response(Question q) { return mapper.toResponse(q,questions.tags(List.of(q.id())).getOrDefault(q.id(),List.of()),statistics.get(q.id())); }
    @Transactional(readOnly=true)
    public QuestionResponse get(UUID id) { return response(find(id,false)); }
    @Transactional(readOnly=true)
    public PageResponse<QuestionResponse> list(UUID actor,QuestionScope scope,UUID university,UUID education,UUID tag,int page,int size) {
        if(page<0||page>10000||size<1||size>100)throw new DomainException(400,"INVALID_REQUEST","Sayfa sınırlarını kontrol et.");
        var filters=new HashMap<String,Object>();
        if(actor!=null)filters.put("actor",actor);if(scope!=null)filters.put("scope",scope.name());
        if(university!=null)filters.put("university",university);if(education!=null)filters.put("education",education);if(tag!=null)filters.put("tag",tag);
        var rows=questions.list(filters,page,size);var tags=questions.tags(rows.stream().map(Question::id).toList());
        var summaries=statistics.summaries(rows.stream().map(Question::id).toList());
        return new PageResponse<>(rows.stream().map(q->mapper.toResponse(q,tags.getOrDefault(q.id(),List.of()),summaries.get(q.id()))).toList(),page,size,questions.count(filters));
    }
    private void actor(UUID id) { accounts.lockActive(id);interaction.requireCompleted(id); }
    private void owner(Question q,UUID actor) {
        if(!q.authorId().equals(actor))throw new DomainException(403,"ACCESS_DENIED","Yalnız kendi sorunu yönetebilirsin.");
        actor(actor);
    }
    private void version(Question q,long version) { if(q.version()!=version)throw new DomainException(409,"STALE_VERSION","Soru değişmiş. Güncel soruyu yükle."); }
    private QuestionContent clean(QuestionContent c) {
        String title=c.title().replaceAll("(?U)\\s+"," ").strip();
        if(title.length()<10||title.length()>200)throw new DomainException(400,"VALIDATION_FAILED","Soru başlığını kontrol et.",Map.of("title","10–200 karakter"));
        if(c.tagIds().size()>5||new HashSet<>(c.tagIds()).size()!=c.tagIds().size())throw new DomainException(400,"VALIDATION_FAILED","Tag seçimini kontrol et.",Map.of("tagIds","En fazla 5 farklı tag"));
        boolean valid=switch(c.scope()) {
            case GENERAL -> c.universityId()==null && c.universityDepartmentId()==null;
            case UNIVERSITY -> c.universityId()!=null && c.universityDepartmentId()==null;
            case UNIVERSITY_DEPARTMENT -> c.universityId()==null && c.universityDepartmentId()!=null;
        };
        if(!valid)throw new DomainException(400,"VALIDATION_FAILED","Soru kapsamıyla eğitim seçimi uyuşmuyor.",Map.of("scope","Kapsama uygun eğitim seç"));
        String body=c.body()==null?null:c.body().strip();
        return new QuestionContent(title,body==null||body.isEmpty()?null:body,c.scope(),c.universityId(),c.universityDepartmentId(),c.tagIds());
    }
    private void references(QuestionContent c,Question old,List<QuestionTagResponse> oldTags) {
        if(c.universityId()!=null)catalog.lockReference(CatalogKind.UNIVERSITY,c.universityId(),old==null||!c.universityId().equals(old.universityId()));
        if(c.universityDepartmentId()!=null)catalog.lockEducation(c.universityDepartmentId(),old==null||!c.universityDepartmentId().equals(old.universityDepartmentId()));
        var existing=oldTags.stream().map(QuestionTagResponse::id).toList();
        for(UUID id:c.tagIds().stream().sorted().toList())catalog.lockReference(CatalogKind.TAG,id,!existing.contains(id));
    }
    private boolean sameContent(Question q,QuestionContent content,List<QuestionTagResponse> oldTags) {
        return q.title().equals(content.title())&&Objects.equals(q.body(),content.body())&&q.scope()==content.scope()
                &&Objects.equals(q.universityId(),content.universityId())&&Objects.equals(q.universityDepartmentId(),content.universityDepartmentId())
                &&new HashSet<>(oldTags.stream().map(QuestionTagResponse::id).toList()).equals(new HashSet<>(content.tagIds()));
    }
    @Transactional
    public QuestionResponse create(UUID actor,QuestionCreateRequest request) {
        actor(actor);
        var existing=questions.existing(actor,request.requestId());
        var content=clean(request.content());
        if(existing.isPresent()) {
            var saved=find(existing.get(),false);var tags=questions.tags(List.of(saved.id())).getOrDefault(saved.id(),List.of());
            if(!sameContent(saved,content,tags))throw new DomainException(409,"REQUEST_CONFLICT","Bu gönderim daha önce kaydedilmiş. Sorularım sayfasından kontrol et.");
            return mapper.toResponse(saved,tags,statistics.get(saved.id()));
        }
        references(content,null,List.of());
        UUID id=UUID.randomUUID();questions.create(id,actor,request.requestId(),content);questions.tags(id,content.tagIds());
        return response(find(id,false));
    }
    @Transactional
    public QuestionResponse update(UUID actor,UUID id,QuestionUpdateRequest request) {
        var q=find(id,true);owner(q,actor);version(q,request.version());
        if(q.archivedAt()!=null)throw new DomainException(409,"QUESTION_ARCHIVED","Arşivlenmiş soru düzenlenemez.");
        var content=clean(request.content());var oldTags=questions.tags(List.of(id)).getOrDefault(id,List.of());references(content,q,oldTags);
        if(sameContent(q,content,oldTags))return response(q);
        questions.update(id,content);questions.tags(id,content.tagIds());return response(find(id,false));
    }
    @Transactional
    public QuestionResponse archive(UUID actor,UUID id,QuestionArchiveRequest request) {
        var q=find(id,true);owner(q,actor);
        if(q.archivedAt()!=null)return response(q);
        version(q,request.version());questions.archive(id);return response(find(id,false));
    }
}
