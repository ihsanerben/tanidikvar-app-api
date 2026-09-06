package com.tanidikvar.api.profile.controller;
import com.tanidikvar.api.profile.dto.PublicProfileResponse;
import com.tanidikvar.api.profile.service.PublicProfileService;
import com.tanidikvar.api.answer.dto.AdminAnswerResponse;
import com.tanidikvar.api.answer.service.AdminAnswerService;
import com.tanidikvar.api.answer.service.AnswerService;
import com.tanidikvar.api.answer.dto.AnswerResponse;
import com.tanidikvar.api.common.dto.PageResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/profiles")
public class PublicProfileController {
 private final PublicProfileService profiles;
 private final AdminAnswerService adminAnswers;
 private final AnswerService answers;
 public PublicProfileController(PublicProfileService profiles,AdminAnswerService adminAnswers,AnswerService answers){this.profiles=profiles;this.adminAnswers=adminAnswers;this.answers=answers;}
 @GetMapping("/{id}") public PublicProfileResponse get(@PathVariable UUID id){return profiles.get(id);}
 @GetMapping("/{id}/comments/community") public PageResponse<AnswerResponse> communityHistory(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){profiles.get(id);return answers.publicHistory(id,page,size);}
 @GetMapping("/{id}/comments/admin") public PageResponse<AdminAnswerResponse> adminHistory(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){profiles.get(id);return adminAnswers.history(id,page,size);}
}
