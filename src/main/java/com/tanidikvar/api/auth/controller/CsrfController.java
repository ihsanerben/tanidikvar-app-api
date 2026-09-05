package com.tanidikvar.api.auth.controller;

import com.tanidikvar.api.auth.dto.CsrfResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
    @GetMapping("/api/auth/csrf")
    @Operation(summary = "Cookie tabanlı istekler için CSRF başlatır")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getToken(), token.getHeaderName()); }
}
