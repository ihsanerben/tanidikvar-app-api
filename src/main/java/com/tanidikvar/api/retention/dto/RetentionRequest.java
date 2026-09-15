package com.tanidikvar.api.retention.dto;
import jakarta.validation.constraints.*;import java.util.UUID;
public record RetentionRequest(@NotBlank @Pattern(regexp="UNIVERSITY|PROGRAM|QUESTION|TANIDIK|ANSWER") String targetType,@NotNull UUID targetId,boolean active) {}
