package com.tanidikvar.api.question.entity;
import java.time.Instant;
import java.util.UUID;
public record Question(UUID id, UUID authorId, String title, String body, QuestionScope scope,
        UUID universityId, UUID universityDepartmentId, Instant createdAt, Instant editedAt,
        Instant archivedAt, Instant deletedAt, long version, String authorName,
        UUID displayUniversityId, String universityName, UUID departmentId, String departmentName) { }
