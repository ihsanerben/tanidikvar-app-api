package com.tanidikvar.api.poll.dto;
import java.time.Instant;import java.util.*;
public record PollResponse(UUID id,UUID authorId,String authorName,UUID universityId,UUID programId,String question,boolean verifiedOnly,Instant closesAt,Instant createdAt,List<PollOptionResponse> options,long totalVotes,long verifiedVoteCount) {}
