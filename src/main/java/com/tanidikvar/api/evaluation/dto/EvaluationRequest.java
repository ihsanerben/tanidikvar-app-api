package com.tanidikvar.api.evaluation.dto;
import jakarta.validation.constraints.*;import java.util.UUID;
public record EvaluationRequest(@NotNull UUID universityId,UUID programId,@Min(1) @Max(5) int rating,@Size(max=2000) String body,EvaluationCriterion criterionKey) {
 public EvaluationRequest { if(criterionKey==null)criterionKey=EvaluationCriterion.GENERAL; }
}
