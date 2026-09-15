package com.tanidikvar.api.retention.controller;
import com.tanidikvar.api.auth.security.SessionPrincipal;import com.tanidikvar.api.common.dto.PageResponse;import com.tanidikvar.api.retention.dto.*;import com.tanidikvar.api.retention.service.RetentionService;import jakarta.validation.Valid;import java.util.UUID;import org.springframework.http.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api") public class RetentionController {
 private final RetentionService service;public RetentionController(RetentionService service){this.service=service;}
 @PutMapping("/me/follows") public RetentionResponse follow(@AuthenticationPrincipal SessionPrincipal user,@Valid @RequestBody RetentionRequest request){return service.follow(user.userId(),request);}
 @GetMapping("/me/follows") public PageResponse<RetentionResponse> follows(@AuthenticationPrincipal SessionPrincipal user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.follows(user.userId(),page,size);}
 @PutMapping("/me/saved") public RetentionResponse save(@AuthenticationPrincipal SessionPrincipal user,@Valid @RequestBody RetentionRequest request){return service.save(user.userId(),request);}
 @GetMapping("/me/saved") public PageResponse<RetentionResponse> saved(@AuthenticationPrincipal SessionPrincipal user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.saved(user.userId(),page,size);}
 @GetMapping("/me/notifications") public PageResponse<NotificationResponse> notifications(@AuthenticationPrincipal SessionPrincipal user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.notifications(user.userId(),page,size);}
 @PutMapping("/me/notifications/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void read(@AuthenticationPrincipal SessionPrincipal user,@PathVariable UUID id){service.read(user.userId(),id);}
 @GetMapping("/me/notification-preferences") public NotificationPreferenceResponse preferences(@AuthenticationPrincipal SessionPrincipal user){return service.preferences(user.userId());}
 @PutMapping("/me/notification-preferences") public NotificationPreferenceResponse preferences(@AuthenticationPrincipal SessionPrincipal user,@Valid@RequestBody NotificationPreferenceRequest request){return service.preferences(user.userId(),request);}
 @GetMapping("/gamification/profiles/{id}") public GamificationResponse score(@PathVariable UUID id){return service.score(id);}
 @GetMapping("/gamification/profiles/{id}/achievements") public java.util.List<AchievementResponse> achievements(@PathVariable UUID id){return service.achievements(id);}
 @PutMapping("/me/gamification/showcase") public java.util.List<AchievementResponse> showcase(@AuthenticationPrincipal SessionPrincipal user,@Valid@RequestBody AchievementShowcaseRequest request){return service.showcase(user.userId(),request);}
 @GetMapping("/gamification/profiles/{id}/annual-report") public AnnualReportResponse report(@PathVariable UUID id,@RequestParam(defaultValue="0") int year){return service.report(id,year);}
 @GetMapping("/gamification/leaderboard") public java.util.List<LeaderboardEntryResponse> leaderboard(@RequestParam(defaultValue="ALL_TIME") String period,@RequestParam(required=false) UUID universityId,@RequestParam(required=false) UUID departmentId,@RequestParam(defaultValue="20") int size){return service.leaderboard(period,universityId,departmentId,size);}
}
