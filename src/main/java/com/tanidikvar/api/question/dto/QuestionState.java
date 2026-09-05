package com.tanidikvar.api.question.dto;
import java.time.Instant;
import java.util.UUID;
public record QuestionState(UUID id, Instant archivedAt) { }
