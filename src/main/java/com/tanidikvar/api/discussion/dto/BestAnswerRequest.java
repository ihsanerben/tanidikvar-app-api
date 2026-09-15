package com.tanidikvar.api.discussion.dto;import jakarta.validation.constraints.*;import java.util.UUID;public record BestAnswerRequest(@NotNull UUID answerId){}
