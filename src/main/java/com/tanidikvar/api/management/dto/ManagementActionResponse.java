package com.tanidikvar.api.management.dto;
public record ManagementActionResponse(java.util.UUID id,java.util.UUID actorId,String action,String targetType,java.util.UUID targetId,String reason,java.time.Instant occurredAt) {}
