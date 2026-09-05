package com.tanidikvar.api.question.dto;
import com.tanidikvar.api.question.entity.QuestionScope;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record QuestionResponse(UUID id, UUID authorId, String authorName, String title, String body,
        QuestionScope scope, UUID universityId, String universityName, UUID universityDepartmentId,
        UUID departmentId, String departmentName, List<QuestionTagResponse> tags,
        Instant createdAt, Instant editedAt, Instant archivedAt, long version) { }
