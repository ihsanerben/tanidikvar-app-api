package com.tanidikvar.api.report.dto;
import java.time.Instant;
import java.util.UUID;
public record ContentReportResponse(UUID id,String targetType,UUID targetId,String reason,String status,Instant createdAt,long version) {}
