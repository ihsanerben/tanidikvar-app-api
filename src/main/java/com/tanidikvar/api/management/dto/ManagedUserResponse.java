package com.tanidikvar.api.management.dto;
public record ManagedUserResponse(java.util.UUID id,String email,String name,String authority,String educationStatus,String universityName,String departmentName,boolean emailVerified,java.time.Instant createdAt,java.time.Instant lastLoginAt,java.time.Instant deletedAt,long version) {}
