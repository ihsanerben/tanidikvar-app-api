package com.tanidikvar.api.engagement.dto;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record ViewRequest(@NotNull UUID openingEventId) { }
