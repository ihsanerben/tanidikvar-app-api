package com.tanidikvar.api.evaluation.dto;
import java.util.List;
public record EvaluationCriterionResponse(EvaluationCriterion criterionKey,String label,double averageRating,long voteCount,List<Long> distribution) {}
