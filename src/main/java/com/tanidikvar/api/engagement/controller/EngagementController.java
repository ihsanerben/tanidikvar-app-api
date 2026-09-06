package com.tanidikvar.api.engagement.controller;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.engagement.dto.*;
import com.tanidikvar.api.engagement.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class EngagementController {
    private final EngagementService engagement;
    private final QuestionStatisticsService statistics;
    public EngagementController(EngagementService engagement,QuestionStatisticsService statistics) {this.engagement=engagement;this.statistics=statistics;}
    @GetMapping("/api/questions/{id}/statistics")
    @Operation(summary="Görünür sorunun tüm zamanlar sayaçları; okuma görüntülenme yazmaz")
    public QuestionStatisticsResponse statistics(@PathVariable UUID id) {return statistics.get(id);}
    @PostMapping("/api/questions/{id}/views") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Başarılı detay açılışını kaydeder; aynı açılış kimliğinin tekrarı sayılmaz, CSRF gerekir")
    public void view(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal principal,@Valid @RequestBody ViewRequest request) {engagement.view(id,request,principal==null?null:principal.userId());}
    @GetMapping("/api/questions/{id}/like") @SecurityRequirement(name="accessCookie")
    public LikeResponse mine(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal principal) {return engagement.mine(id,principal.userId());}
    @PutMapping("/api/questions/{id}/like") @SecurityRequirement(name="accessCookie")
    @Operation(summary="Profil sahibi beğenir veya geri alır; aynı kayıt ve ilk beğeni tarihi korunur")
    public LikeResponse like(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal principal,@Valid @RequestBody LikeRequest request) {return engagement.like(id,principal.userId(),request);}
}
