package com.tanidikvar.api.retention.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LeaderboardEntryResponse(
    UUID userId,
    String displayName,
    long points,
    long eventCount,
    String title,
    List<String> badges,
    Instant lastEventAt
) {}
