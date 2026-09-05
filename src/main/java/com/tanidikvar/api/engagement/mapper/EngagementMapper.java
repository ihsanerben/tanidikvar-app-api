package com.tanidikvar.api.engagement.mapper;
import com.tanidikvar.api.engagement.dto.*;
import com.tanidikvar.api.engagement.entity.*;
import org.springframework.stereotype.Component;
@Component
public class EngagementMapper {
    public LikeResponse toResponse(QuestionLike like) { return new LikeResponse(like.liked(),like.version()); }
    public QuestionStatisticsResponse toResponse(QuestionStatistics s) { return new QuestionStatisticsResponse(s.views(),s.likes(),s.communityAnswers(),s.adminAnswers(),s.communityAnswers()+s.adminAnswers()); }
}
