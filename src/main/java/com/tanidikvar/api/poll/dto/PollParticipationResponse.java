package com.tanidikvar.api.poll.dto;
import java.util.UUID;
public record PollParticipationResponse(UUID pollId,UUID optionId) {}
