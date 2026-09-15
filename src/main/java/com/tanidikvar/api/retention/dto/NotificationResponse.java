package com.tanidikvar.api.retention.dto;
import java.time.Instant;import java.util.UUID;
public record NotificationResponse(UUID id,String type,String title,String body,String targetType,UUID targetId,Instant createdAt,Instant readAt,long version) {}
