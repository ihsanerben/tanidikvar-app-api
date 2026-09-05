package com.tanidikvar.api.auth.controller;

import com.tanidikvar.api.auth.dto.*;
import com.tanidikvar.api.auth.entity.ActionPurpose;
import com.tanidikvar.api.auth.security.AuthCookies;
import com.tanidikvar.api.auth.service.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AuthenticationService authentication;
    private final AccountActionService actions;
    private final AuthCookies cookies;
    public AuthenticationController(AuthenticationService authentication, AccountActionService actions, AuthCookies cookies) {
        this.authentication = authentication; this.actions = actions; this.cookies = cookies;
    }
    @PostMapping("/register") @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Kayıt talebi; e-posta uygunsa doğrulama bağlantısı gönderilir. Mevcut hesaplar da aynı yanıtı alır.")
    public void register(@Valid @RequestBody RegisterRequest body) { actions.register(body.email(), body.password()); }

    @PostMapping("/login")
    @Operation(summary = "Doğrulanmış e-posta ve şifreyle giriş; access/refresh HttpOnly cookie olarak verilir.")
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest body, HttpServletResponse response) {
        var session = authentication.login(body.email(), body.password());
        cookies.write(response, session); return session.user();
    }
    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT atomik olarak döndürülür; eski token tekrar kullanılırsa bütün aile iptal edilir.")
    public CurrentUserResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        var session = authentication.refresh(cookies.read(request, AuthCookies.REFRESH));
        cookies.write(response, session); return session.user();
    }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authentication.logout(cookies.read(request, AuthCookies.REFRESH), cookies.read(request, AuthCookies.ACCESS)); cookies.clear(response);
    }
    @PostMapping("/resend-verification") @ResponseStatus(HttpStatus.ACCEPTED)
    public void resend(@Valid @RequestBody EmailRequest body) { actions.request(body.email(), ActionPurpose.VERIFY_EMAIL); }
    @PostMapping("/verify-email") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody TokenRequest body) { actions.verify(body.token()); }
    @PostMapping("/forgot-password") @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgot(@Valid @RequestBody EmailRequest body) { actions.request(body.email(), ActionPurpose.RESET_PASSWORD); }
    @PostMapping("/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest body, HttpServletResponse response) {
        actions.resetPassword(body.token(), body.password()); cookies.clear(response);
    }
}
