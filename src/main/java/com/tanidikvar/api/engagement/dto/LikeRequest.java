package com.tanidikvar.api.engagement.dto;
import jakarta.validation.constraints.*;
public record LikeRequest(@NotNull Boolean liked,@NotNull @PositiveOrZero Long version) { }
