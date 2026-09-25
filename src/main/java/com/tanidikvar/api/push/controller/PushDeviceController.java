package com.tanidikvar.api.push.controller;

import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.push.dto.PushDeviceRequest;
import com.tanidikvar.api.push.service.PushDeviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/push-device")
public class PushDeviceController {
    private final PushDeviceService service;
    public PushDeviceController(PushDeviceService service) { this.service=service; }
    @PutMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@AuthenticationPrincipal SessionPrincipal principal, @Valid @RequestBody PushDeviceRequest request) {
        service.register(principal,request);
    }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@AuthenticationPrincipal SessionPrincipal principal) { service.unregister(principal); }
}
