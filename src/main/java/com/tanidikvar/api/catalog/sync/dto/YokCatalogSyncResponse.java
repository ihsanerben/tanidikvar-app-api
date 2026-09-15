package com.tanidikvar.api.catalog.sync.dto;

import java.time.Instant;
import java.util.UUID;

public record YokCatalogSyncResponse(
        UUID id,
        String operation,
        String status,
        String snapshotChecksum,
        int universitiesSeen,
        int programsSeen,
        int optionsSeen,
        Instant startedAt,
        Instant completedAt,
        String failureReason,
        YokCatalogQualityReport qualityReport) { }
