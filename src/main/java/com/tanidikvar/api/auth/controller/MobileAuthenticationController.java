package com.tanidikvar.api.auth.controller;

import com.tanidikvar.api.auth.dto.*;
import com.tanidikvar.api.auth.entity.ActionPurpose;
import com.tanidikvar.api.auth.mapper.MobileSessionMapper;
import com.tanidikvar.api.auth.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/auth/mobile")
public class MobileAuthenticationController {
    private final AuthenticationService authentication;
    private final AccountActionService actions;
    private final MobileSessionMapper mapper;

    public MobileAuthenticationController(AuthenticationService authentication, AccountActionService actions,
            MobileSessionMapper mapper) {
        this.authentication = authentication; this.actions = actions; this.mapper = mapper;
    }
    @PostMapping("/login")
    @Operation(summary = "Mobil giriş; tokenlar JSON gövdesinde döner, cookie üretilmez.")
    public MobileSessionResponse login(@Valid @RequestBody LoginRequest body) {
        return mapper.toResponse(authentication.login(body.email(), body.password()));
    }
    @PostMapping("/refresh")
    @Operation(summary = "Mobil refresh rotation; tekrar kullanım oturum ailesini iptal eder.")
    public MobileSessionResponse refresh(@Valid @RequestBody RefreshSessionRequest body) {
        return mapper.toResponse(authentication.refresh(body.refreshToken()));
    }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshSessionRequest body) {
        authentication.logout(body.refreshToken(), null);
    }
    @PostMapping("/register") @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @RequestBody RegisterRequest body) { actions.register(body.email(), body.password(), true); }
    @PostMapping("/resend-verification") @ResponseStatus(HttpStatus.ACCEPTED)
    public void resend(@Valid @RequestBody EmailRequest body) { actions.request(body.email(), ActionPurpose.VERIFY_EMAIL, true); }
    @PostMapping("/verify-email") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody TokenRequest body) { actions.verify(body.token()); }
    @PostMapping("/forgot-password") @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgot(@Valid @RequestBody EmailRequest body) { actions.request(body.email(), ActionPurpose.RESET_PASSWORD, true); }
    @PostMapping("/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest body) { actions.resetPassword(body.token(), body.password()); }
}
