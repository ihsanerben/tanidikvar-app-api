package com.tanidikvar.api.answer.dto;
import java.util.UUID;
import java.time.Instant;
public record AssignmentResponse(UUID questionId,String questionTitle,boolean assigned,long version,Instant assignedAt,Instant archivedAt) {}

