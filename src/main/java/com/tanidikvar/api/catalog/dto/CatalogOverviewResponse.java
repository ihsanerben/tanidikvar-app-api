package com.tanidikvar.api.catalog.dto;

import java.time.Instant;
import java.util.List;

public record CatalogOverviewResponse(long universityCount,long programCount,long optionCount,long statisticsCount,
        long rankedOptionCount,List<LabelCountResponse> institutionTypes,List<LabelCountResponse> cities,
        List<LabelCountResponse> degreeLevels,List<LabelCountResponse> scoreTypes,
        List<YearCatalogStatisticsResponse> yearly,Instant lastSynchronizedAt) { }
