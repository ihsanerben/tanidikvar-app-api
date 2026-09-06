package com.tanidikvar.api.answer.dto;
import java.time.Instant;
import java.util.UUID;
public record AnswerResponse(UUID id, UUID questionId, UUID authorId, String authorName, UUID avatarFileId, String educationStatus, String answerKind,
        String body, Instant publishedAt, Instant editedAt, Instant deletedAt, Instant moderatedAt, long version) { }
