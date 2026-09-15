package com.tanidikvar.api.decision.dto;
import java.util.List;
public record SentimentSummaryResponse(List<CareerCategoryResponse> positives,List<CareerCategoryResponse> negatives) {}
