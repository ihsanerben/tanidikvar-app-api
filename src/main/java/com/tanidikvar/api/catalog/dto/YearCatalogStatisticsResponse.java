package com.tanidikvar.api.catalog.dto;

import java.math.BigDecimal;

public record YearCatalogStatisticsResponse(int year,long programCount,long quota,long placed,
        BigDecimal fillRate,long preferences) { }
