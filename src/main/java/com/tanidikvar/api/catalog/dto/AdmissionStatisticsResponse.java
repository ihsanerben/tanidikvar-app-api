package com.tanidikvar.api.catalog.dto;

import java.math.BigDecimal;

public record AdmissionStatisticsResponse(int year,Integer quota,Integer placed,BigDecimal minimumScore,
        BigDecimal maximumScore,Integer successRank,Integer placedMale,Integer placedFemale,
        BigDecimal averageSecondaryScore,Integer totalPreferences,BigDecimal demandPerQuota,
        BigDecimal averagePreferenceRank) { }
