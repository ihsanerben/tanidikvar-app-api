package com.tanidikvar.api.answer.entity;
import java.time.Instant;
import java.util.UUID;
public record Answer(UUID id, UUID questionId, UUID authorId, String authorName, String body,
        Instant publishedAt, Instant editedAt, Instant deletedAt, Instant moderatedAt, long version) { }
