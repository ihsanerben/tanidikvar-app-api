package com.tanidikvar.api.poll.controller;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.poll.dto.PollParticipationResponse;
import com.tanidikvar.api.poll.service.PollService;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/me/poll-votes")
public class PollParticipationController {
 private final PollService service;
 public PollParticipationController(PollService service){this.service=service;}
 @GetMapping public List<PollParticipationResponse> votes(@AuthenticationPrincipal SessionPrincipal user,@RequestParam @Size(min=1,max=100) List<UUID> pollIds){return service.participation(user.userId(),pollIds);}
}
