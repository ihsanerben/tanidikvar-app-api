package com.tanidikvar.api.catalog.entity;
import java.time.Instant;
import java.util.UUID;
public record CatalogEntry(UUID id, String name, Instant deletedAt, long version) { }
