package com.tanidikvar.api.management.mapper;
import com.tanidikvar.api.management.entity.*;
import com.tanidikvar.api.management.dto.*;
import org.springframework.stereotype.Component;
@Component
public class ManagementMapper {
 public ManagedUserResponse user(ManagedUser a){return new ManagedUserResponse(a.id(),a.email(),a.name(),a.authority(),a.educationStatus(),a.emailVerified(),a.createdAt(),a.deletedAt(),a.version());}
 public ManagedContentResponse content(ManagedContent a){return new ManagedContentResponse(a.id(),a.kind(),a.questionId(),a.title(),a.body(),a.authorName(),a.deletedAt(),a.moderatedAt(),a.archivedAt(),a.questionHidden(),a.version());}
 public ManagementStatsResponse stats(ManagementStats a){return new ManagementStatsResponse(a.activeUsers(),a.disabledUsers(),a.activeAdmins(),a.pendingApplications(),a.activeQuestions(),a.archivedQuestions(),a.hiddenQuestions(),a.communityAnswers(),a.adminAnswers(),a.likes(),a.views());}
 public ManagementActionResponse action(ManagementAction a){return new ManagementActionResponse(a.id(),a.actorId(),a.action(),a.targetType(),a.targetId(),a.reason(),a.occurredAt());}
}
