package com.tanidikvar.api.management.dto;
public record ManagedContentResponse(java.util.UUID id,String kind,java.util.UUID questionId,String title,String body,String authorName,java.time.Instant deletedAt,java.time.Instant moderatedAt,java.time.Instant archivedAt,boolean questionHidden,long version) {}
