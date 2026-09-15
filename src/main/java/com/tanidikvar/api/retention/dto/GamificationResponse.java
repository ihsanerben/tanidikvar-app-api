package com.tanidikvar.api.retention.dto;
import java.time.Instant;import java.util.*;
public record GamificationResponse(UUID userId,long totalPoints,long eventCount,String title,List<String> badges,List<String> expertise,Instant lastEventAt) {}
