package com.tanidikvar.api.engagement.service;
import com.tanidikvar.api.engagement.dto.QuestionStatisticsResponse;
import com.tanidikvar.api.engagement.mapper.EngagementMapper;
import com.tanidikvar.api.engagement.repository.EngagementRepository;
import com.tanidikvar.api.common.error.DomainException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class QuestionStatisticsService {
    private final EngagementRepository repository;
    private final EngagementMapper mapper;
    public QuestionStatisticsService(EngagementRepository repository,EngagementMapper mapper) {this.repository=repository;this.mapper=mapper;}
    public Map<UUID,QuestionStatisticsResponse> summaries(Collection<UUID> ids) {
        var result=new HashMap<UUID,QuestionStatisticsResponse>();
        repository.statistics(ids).forEach((id,s)->result.put(id,mapper.toResponse(s)));return result;
    }
    public QuestionStatisticsResponse get(UUID id) {
        var result=summaries(List.of(id)).get(id);
        if(result==null)throw new DomainException(404,"NOT_FOUND","Soru bulunamadı.");return result;
    }
}
