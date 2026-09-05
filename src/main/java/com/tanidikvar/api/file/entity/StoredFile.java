package com.tanidikvar.api.file.entity;
import java.util.UUID;
public record StoredFile(UUID id, UUID ownerId, String purpose, String storageKey, String contentType, long byteSize) {}

