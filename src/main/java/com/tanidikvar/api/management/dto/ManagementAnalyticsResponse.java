package com.tanidikvar.api.management.dto;
import java.time.LocalDate;
import java.util.List;
public record ManagementAnalyticsResponse(LocalDate dateFrom,LocalDate dateTo,String timezone,ManagementAnalyticsTotalsResponse totals,List<ManagementAnalyticsPointResponse> points) {}
