package com.tanidikvar.api.catalog.dto;
import java.time.Instant;
import java.util.UUID;
public record CatalogResponse(UUID id, String name, Instant deletedAt, long version) { }
