package com.tanidikvar.api.management.entity;
public record ManagedContent(java.util.UUID id,String kind,java.util.UUID questionId,String title,String body,String authorName,java.time.Instant deletedAt,java.time.Instant moderatedAt,java.time.Instant archivedAt,boolean questionHidden,long version) {}
