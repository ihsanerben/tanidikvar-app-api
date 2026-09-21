package com.tanidikvar.api.report.dto;

import java.time.Instant;
import java.util.UUID;

public record ManagedContentReportResponse(UUID id,String targetType,UUID targetId,UUID questionId,String questionTitle,String contentBody,UUID authorId,String authorName,UUID reporterId,String reporterName,String reason,String status,Instant createdAt,String resolutionReason,long version) {}
