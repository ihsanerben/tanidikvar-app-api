package com.tanidikvar.api.auth.controller;

import com.tanidikvar.api.auth.dto.CurrentUserResponse;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class CurrentUserController {
    @GetMapping("/api/me") @SecurityRequirement(name = "accessCookie")
    public CurrentUserResponse me(@AuthenticationPrincipal SessionPrincipal principal) { return principal.user(); }
}
