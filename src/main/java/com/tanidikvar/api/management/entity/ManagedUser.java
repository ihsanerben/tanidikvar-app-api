package com.tanidikvar.api.management.entity;
public record ManagedUser(java.util.UUID id,String email,String name,String authority,String educationStatus,String universityName,String departmentName,boolean emailVerified,java.time.Instant createdAt,java.time.Instant deletedAt,long version) {}
