package com.tanidikvar.api.catalog.dto;
import java.time.Instant;
import java.util.UUID;
public record EducationResponse(UUID id, UUID universityId, String universityName, UUID departmentId, String departmentName,
        Instant deletedAt, boolean available, long version) { }
