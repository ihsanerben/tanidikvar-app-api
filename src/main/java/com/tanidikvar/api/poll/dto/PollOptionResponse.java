package com.tanidikvar.api.poll.dto;
import java.util.UUID;
public record PollOptionResponse(UUID id,String label,int position,long voteCount) {}
