package com.tanidikvar.api.poll.dto;
import jakarta.validation.constraints.*;import java.time.Instant;import java.util.*;
public record PollCreateRequest(@NotNull UUID universityId,UUID programId,@NotBlank @Size(min=10,max=300) String question,@NotNull @Size(min=2,max=6) List<@NotBlank @Size(max=120) String> options,boolean verifiedOnly,@Future Instant closesAt) {}
