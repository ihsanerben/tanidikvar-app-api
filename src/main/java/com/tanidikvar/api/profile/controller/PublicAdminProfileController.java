package com.tanidikvar.api.profile.controller;
import com.tanidikvar.api.profile.dto.PublicAdminProfileResponse;
import com.tanidikvar.api.profile.service.PublicAdminProfileService;
import com.tanidikvar.api.answer.dto.AdminAnswerResponse;
import com.tanidikvar.api.answer.service.AdminAnswerService;
import com.tanidikvar.api.common.dto.PageResponse;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
@RestController
public class PublicAdminProfileController {
 private final PublicAdminProfileService profiles;private final AdminAnswerService answers;
 public PublicAdminProfileController(PublicAdminProfileService profiles,AdminAnswerService answers){this.profiles=profiles;this.answers=answers;}
 @GetMapping("/api/admins")
 @Operation(summary="Herkese açık Admin profillerini isimle arar; geçmiş Adminler işaretlenir, sayfalıdır")
 public PageResponse<PublicAdminProfileResponse> search(@RequestParam(required=false) String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return profiles.search(q,page,size);}
 @GetMapping("/api/admins/{id}") public PublicAdminProfileResponse get(@PathVariable UUID id){return profiles.get(id);}
 @GetMapping("/api/admins/{id}/answers") public PageResponse<AdminAnswerResponse> history(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){profiles.get(id);return answers.history(id,page,size);}
}

