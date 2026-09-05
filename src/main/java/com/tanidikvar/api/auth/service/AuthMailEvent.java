package com.tanidikvar.api.auth.service;
import com.tanidikvar.api.auth.entity.ActionPurpose;
public record AuthMailEvent(String email, String token, ActionPurpose purpose) {
    @Override public String toString() { return "AuthMailEvent[REDACTED]"; }
}
