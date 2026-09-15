package com.tanidikvar.api.retention.dto;
import java.time.Instant;import java.util.UUID;
public record RetentionResponse(UUID id,String targetType,UUID targetId,Instant createdAt,boolean active,long version) {}
