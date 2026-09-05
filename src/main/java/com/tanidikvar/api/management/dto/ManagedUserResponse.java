package com.tanidikvar.api.management.dto;
public record ManagedUserResponse(java.util.UUID id,String email,String name,String authority,String educationStatus,boolean emailVerified,java.time.Instant createdAt,java.time.Instant deletedAt,long version) {}
