package com.tanidikvar.api.poll.controller;
import com.tanidikvar.api.auth.security.SessionPrincipal;import com.tanidikvar.api.common.dto.PageResponse;import com.tanidikvar.api.poll.dto.*;import com.tanidikvar.api.poll.service.PollService;import jakarta.validation.Valid;import java.util.UUID;import org.springframework.http.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/polls") public class PollController{
 private final PollService service;public PollController(PollService service){this.service=service;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public PollResponse create(@AuthenticationPrincipal SessionPrincipal user,@Valid @RequestBody PollCreateRequest request){return service.create(user.userId(),request);}
 @GetMapping public PageResponse<PollResponse> list(@RequestParam UUID universityId,@RequestParam(required=false) UUID programId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(universityId,programId,page,size);}
 @PutMapping("/{id}/vote") public PollResponse vote(@AuthenticationPrincipal SessionPrincipal user,@PathVariable UUID id,@Valid @RequestBody PollVoteRequest request){return service.vote(user.userId(),id,request);}
}
