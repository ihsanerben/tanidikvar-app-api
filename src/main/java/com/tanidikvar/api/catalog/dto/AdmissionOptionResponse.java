package com.tanidikvar.api.catalog.dto;

import java.util.List;
import java.util.UUID;

public record AdmissionOptionResponse(UUID id,String programCode,String faculty,String scoreType,
        Integer durationYears,List<AdmissionStatisticsResponse> statistics) { }
