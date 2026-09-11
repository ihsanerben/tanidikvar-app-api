package com.tanidikvar.api.management.mapper;
import com.tanidikvar.api.management.entity.*;
import com.tanidikvar.api.management.dto.*;
import org.springframework.stereotype.Component;
@Component
public class ManagementMapper {
 public ManagedUserResponse user(ManagedUser a){return new ManagedUserResponse(a.id(),a.email(),a.name(),a.authority(),a.educationStatus(),a.universityName(),a.departmentName(),a.emailVerified(),a.createdAt(),a.lastLoginAt(),a.deletedAt(),a.version());}
 public ManagedContentResponse content(ManagedContent a){return new ManagedContentResponse(a.id(),a.kind(),a.questionId(),a.authorId(),a.title(),a.body(),a.authorName(),a.questionAuthorId(),a.questionAuthorName(),a.createdAt(),a.deletedAt(),a.moderatedAt(),a.archivedAt(),a.questionHidden(),a.viewCount(),a.likeCount(),a.communityAnswerCount(),a.adminAnswerCount(),a.version());}
 public ManagementStatsResponse stats(ManagementStats a){return new ManagementStatsResponse(a.activeUsers(),a.disabledUsers(),a.activeAdmins(),a.pendingApplications(),a.activeQuestions(),a.archivedQuestions(),a.hiddenQuestions(),a.communityAnswers(),a.adminAnswers(),a.likes(),a.views());}
 public ManagementAnalyticsPointResponse analyticsPoint(ManagementAnalyticsPoint a){return new ManagementAnalyticsPointResponse(a.date(),a.users(),a.questions(),a.communityAnswers(),a.adminAnswers(),a.views(),a.likes(),a.applications(),a.approvedApplications(),a.rejectedApplications());}
 public ManagementActionResponse action(ManagementAction a){return new ManagementActionResponse(a.id(),a.actorId(),a.action(),a.targetType(),a.targetId(),a.reason(),a.occurredAt());}
}
