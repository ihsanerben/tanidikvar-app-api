package com.tanidikvar.api.evaluation.dto;
import java.time.Instant;import java.util.UUID;
public record EvaluationResponse(UUID id,UUID authorId,String authorName,UUID universityId,UUID programId,int rating,String body,Instant createdAt,Instant updatedAt,long version) {}
