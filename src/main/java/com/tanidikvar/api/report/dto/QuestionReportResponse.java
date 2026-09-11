package com.tanidikvar.api.report.dto;
import java.time.Instant;import java.util.UUID;
public record QuestionReportResponse(UUID id,UUID questionId,String questionTitle,UUID questionAuthorId,String questionAuthorName,UUID reporterId,String reporterName,String reason,String status,Instant createdAt,UUID reviewedBy,Instant reviewedAt,String resolutionReason,long version) {}
