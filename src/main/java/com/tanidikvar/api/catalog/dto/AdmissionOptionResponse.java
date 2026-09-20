package com.tanidikvar.api.catalog.dto;

import java.util.List;
import java.util.UUID;

public record AdmissionOptionResponse(UUID id,String programCode,String faculty,String scoreType,
        String educationType,String language,String scholarship,String specialQuotaType,Integer durationYears,
        java.math.BigDecimal annualFee,
        List<AdmissionStatisticsResponse> statistics) { }
