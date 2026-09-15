package com.tanidikvar.api.catalog.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProgramSummaryResponse(UUID id,UUID educationId,UUID departmentId,UUID universityId,String universityName,String city,String institutionType,
        String name,String degreeLevel,List<String> faculties,List<String> scoreTypes,Integer durationYears,
        long optionCount,Integer currentBestRank,BigDecimal currentMinimumScore,long currentQuota,long currentPlaced) { }
