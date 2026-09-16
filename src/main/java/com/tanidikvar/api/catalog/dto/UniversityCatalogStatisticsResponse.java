package com.tanidikvar.api.catalog.dto;

import java.util.List;
import java.util.UUID;

public record UniversityCatalogStatisticsResponse(UUID universityId,long facultyCount,long programCount,long optionCount,List<LabelCountResponse> academicUnits,
        List<LabelCountResponse> degreeLevels,List<LabelCountResponse> scoreTypes,
        List<YearCatalogStatisticsResponse> yearly,List<ProgramSummaryResponse> bestRankedPrograms) { }
