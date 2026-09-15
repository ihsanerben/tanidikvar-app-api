package com.tanidikvar.api.decision.dto;
import java.math.BigDecimal;import java.time.Instant;import java.util.List;
public record CareerSummaryResponse(long sampleSize,long verifiedSampleSize,BigDecimal averageJobSearchMonths,long graduateStudyCount,List<CareerCategoryResponse> sectors,List<CareerCategoryResponse> firstRoles,List<CareerCategoryResponse> companyTypes,Instant updatedAt,boolean privacyThresholdMet) {}
