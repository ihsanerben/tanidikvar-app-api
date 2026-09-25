package com.tanidikvar.api.auth.controller;

import com.tanidikvar.api.auth.dto.CloseAccountRequest;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.auth.service.AuthenticationService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
@SecurityRequirement(name = "accessCookie")
@SecurityRequirement(name = "bearerAuth")
public class SessionAccountController {
    private final AuthenticationService authentication;
    public SessionAccountController(AuthenticationService authentication) { this.authentication = authentication; }

    @PostMapping("/logout-all") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal SessionPrincipal principal) { authentication.logoutAll(principal.userId()); }

    @PostMapping("/close-account") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@AuthenticationPrincipal SessionPrincipal principal, @Valid @RequestBody CloseAccountRequest body) {
        authentication.closeAccount(principal.userId(), body.password());
    }
}
