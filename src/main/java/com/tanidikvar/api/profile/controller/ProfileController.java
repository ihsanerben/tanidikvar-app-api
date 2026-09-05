package com.tanidikvar.api.profile.controller;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.profile.dto.*;
import com.tanidikvar.api.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/me/profile")
@SecurityRequirement(name="accessCookie")
public class ProfileController {
    private final ProfileService profiles;
    public ProfileController(ProfileService profiles) { this.profiles=profiles; }
    @GetMapping public ProfileResponse get(@AuthenticationPrincipal SessionPrincipal principal) { return profiles.get(principal.userId()); }
    @PutMapping public ProfileResponse update(@AuthenticationPrincipal SessionPrincipal principal,@Valid @RequestBody ProfileRequest request) {
        return profiles.update(principal.userId(),request);
    }
}
