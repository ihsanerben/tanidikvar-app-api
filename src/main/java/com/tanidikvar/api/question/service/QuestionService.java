package com.tanidikvar.api.question.service;
import com.tanidikvar.api.auth.service.AccountAccessService;
import com.tanidikvar.api.catalog.entity.CatalogKind;
import com.tanidikvar.api.catalog.service.CatalogService;
import com.tanidikvar.api.catalog.service.ProgramCatalogService;
import com.tanidikvar.api.common.dto.PageResponse;
import com.tanidikvar.api.common.error.DomainException;
import com.tanidikvar.api.profile.service.InteractionPolicy;
import com.tanidikvar.api.question.dto.*;
import com.tanidikvar.api.question.entity.*;
import com.tanidikvar.api.question.mapper.QuestionMapper;
import com.tanidikvar.api.question.repository.QuestionRepository;
import java.util.*;
import java.time.Clock;
import java.sql.Timestamp;
import com.tanidikvar.api.common.dto.SearchQuery;
import org.springframework.transaction.annotation.Isolation;
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
    private final ProgramCatalogService programs;
    private final Clock clock;
    public QuestionService(QuestionRepository questions,QuestionMapper mapper,AccountAccessService accounts,InteractionPolicy interaction,CatalogService catalog,ProgramCatalogService programs,com.tanidikvar.api.engagement.service.QuestionStatisticsService statistics,Clock clock) {
        this.clock=clock;
        this.statistics=statistics;
        this.questions=questions;this.mapper=mapper;this.accounts=accounts;this.interaction=interaction;this.catalog=catalog;this.programs=programs;
    }
    private Question find(UUID id,boolean lock) { return questions.find(id,lock).orElseThrow(()->new DomainException(404,"NOT_FOUND","Soru bulunamadı.")); }
    private QuestionResponse response(Question q) { return mapper.toResponse(q,questions.tags(List.of(q.id())).getOrDefault(q.id(),List.of()),statistics.get(q.id())); }
    @Transactional(readOnly=true)
    public QuestionResponse get(UUID id) { return response(find(id,false)); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<QuestionResponse> list(UUID actor,QuestionScope scope,UUID university,UUID tag,int page,int size) {
        return discover(actor,scope,university,tag,null,null,null,null,null,null,null,"NEWEST",page,size);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<QuestionResponse> mine(UUID actor,String status,int page,int size) {
        String normalized=status==null?"ACTIVE":status.toUpperCase(Locale.ROOT);
        if(!Set.of("ACTIVE","ARCHIVED").contains(normalized))throw new DomainException(400,"INVALID_STATUS","Soru durumunu kontrol et.");
        SearchQuery.page(page,size);
        var filters=new HashMap<String,Object>();filters.put("actor",actor);filters.put("archived",normalized.equals("ARCHIVED"));
        var rows=questions.list(filters,"NEWEST",page,size);var ids=rows.stream().map(Question::id).toList();var tags=questions.tags(ids);var summaries=statistics.summaries(ids);
        return new PageResponse<>(rows.stream().map(q->mapper.toResponse(q,tags.getOrDefault(q.id(),List.of()),summaries.get(q.id()))).toList(),page,size,questions.count(filters));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<QuestionResponse> discover(UUID actor,QuestionScope scope,UUID university,UUID tag,UUID department,UUID admin,String city,Boolean answered,Boolean verifiedAnswer,String query,PopularPeriod period,String sort,int page,int size) {
        SearchQuery.page(page,size);String search=SearchQuery.clean(query);
        var filters=new HashMap<String,Object>();
        if(actor!=null)filters.put("actor",actor);if(scope!=null)filters.put("scope",scope.name());
        if(university!=null)filters.put("university",university);if(tag!=null)filters.put("tag",tag);
        if(department!=null)filters.put("department",department);if(admin!=null)filters.put("admin",admin);if(!search.isEmpty())filters.put("query",search);
        String cityQuery=SearchQuery.clean(city);if(!cityQuery.isEmpty())filters.put("city",cityQuery);
        if(answered!=null)filters.put("answered",answered);if(verifiedAnswer!=null)filters.put("verifiedAnswer",verifiedAnswer);
        if(period!=null) {
            var until=clock.instant();filters.put("until",Timestamp.from(until));filters.put("since",Timestamp.from(until.minusSeconds(period.seconds())));
            filters.put("seconds",period.seconds());filters.put("viewWeight",1);filters.put("likeWeight",5);filters.put("communityWeight",10);filters.put("adminWeight",25);
        }
        String ordering=sort==null?"NEWEST":sort.toUpperCase(Locale.ROOT);if(period==null&&!Set.of("NEWEST","OLDEST","MOST_VIEWED","MOST_LIKED","MOST_COMMENTED").contains(ordering))throw new DomainException(400,"INVALID_SORT","Sıralama seçeneğini kontrol et.");
        var rows=period==null?questions.list(filters,ordering,page,size):questions.popular(filters,page,size);
        var ids=rows.stream().map(Question::id).toList();var tags=questions.tags(ids);var summaries=statistics.summaries(ids);
        return new PageResponse<>(rows.stream().map(q->mapper.toResponse(q,tags.getOrDefault(q.id(),List.of()),summaries.get(q.id()))).toList(),page,size,
            period==null?questions.count(filters):questions.popularCount(filters));
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
            case GENERAL -> c.universityId()==null && c.programId()==null && c.departmentId()==null;
            case UNIVERSITY -> c.universityId()!=null && c.programId()==null && c.departmentId()==null;
            case UNIVERSITY_DEPARTMENT -> c.universityId()!=null && (c.programId()!=null || c.departmentId()!=null);
        };
        if(!valid)throw new DomainException(400,"VALIDATION_FAILED","Soru kapsamıyla eğitim seçimi uyuşmuyor.",Map.of("scope","Kapsama uygun eğitim seç"));
        String body=c.body()==null?null:c.body().strip();
        UUID programId=c.programId(),departmentId=c.departmentId();
        if(programId!=null){var selected=programs.detail(programId).summary();if(!selected.universityId().equals(c.universityId()))throw new DomainException(400,"VALIDATION_FAILED","Program seçilen üniversiteye ait değil.");departmentId=selected.departmentId();}
        return new QuestionContent(title,body==null||body.isEmpty()?null:body,c.scope(),c.universityId(),programId,departmentId,c.tagIds());
    }
    private void references(QuestionContent c,Question old,List<QuestionTagResponse> oldTags) {
        if(c.universityId()!=null)catalog.lockReference(CatalogKind.UNIVERSITY,c.universityId(),old==null||!c.universityId().equals(old.universityId()));
        if(c.departmentId()!=null)catalog.lockReference(CatalogKind.DEPARTMENT,c.departmentId(),old==null||!c.departmentId().equals(old.departmentId()));
        var existing=oldTags.stream().map(QuestionTagResponse::id).toList();
        for(UUID id:c.tagIds().stream().sorted().toList())catalog.lockReference(CatalogKind.TAG,id,!existing.contains(id));
    }
    private boolean sameContent(Question q,QuestionContent content,List<QuestionTagResponse> oldTags) {
        return q.title().equals(content.title())&&Objects.equals(q.body(),content.body())&&q.scope()==content.scope()
                &&Objects.equals(q.universityId(),content.universityId())&&Objects.equals(q.programId(),content.programId())&&Objects.equals(q.departmentId(),content.departmentId())
                &&new HashSet<>(oldTags.stream().map(QuestionTagResponse::id).toList()).equals(new HashSet<>(content.tagIds()));
    }
    @Transactional
    public QuestionResponse create(UUID actor,QuestionCreateRequest request) {
        actor(actor);
        var existing=questions.existing(actor,request.requestId());
        var content=clean(request.content());
        if(existing.isPresent()) {
            var saved=find(existing.get(),false);var tags=questions.tags(List.of(saved.id())).getOrDefault(saved.id(),List.of());
            if(!sameContent(saved,content,tags))throw new DomainException(409,"REQUEST_CONFLICT","Bu gönderim daha önce kaydedilmiş. Sorunun detayını kontrol et.");
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
    @Transactional
    public QuestionResponse restore(UUID actor,UUID id,QuestionArchiveRequest request) {
        var q=find(id,true);owner(q,actor);
        if(q.archivedAt()==null)return response(q);
        version(q,request.version());questions.restore(id);return response(find(id,false));
    }
}
