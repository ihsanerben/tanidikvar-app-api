package com.tanidikvar.api.answer.dto;
import jakarta.validation.constraints.NotNull;
public record AnswerLikeRequest(@NotNull Boolean liked) {}
