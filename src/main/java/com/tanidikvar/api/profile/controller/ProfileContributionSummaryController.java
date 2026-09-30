package com.tanidikvar.api.profile.controller;

import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.profile.dto.ProfileContributionSummaryResponse;
import com.tanidikvar.api.profile.repository.ProfileContributionSummaryRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileContributionSummaryController {
    private final ProfileContributionSummaryRepository summaries;

    public ProfileContributionSummaryController(ProfileContributionSummaryRepository summaries) { this.summaries = summaries; }

    @GetMapping("/api/me/contribution-summary")
    @SecurityRequirement(name = "accessCookie")
    @SecurityRequirement(name = "bearerAuth")
    public ProfileContributionSummaryResponse get(@AuthenticationPrincipal SessionPrincipal principal) {
        return summaries.get(principal.userId());
    }
}
