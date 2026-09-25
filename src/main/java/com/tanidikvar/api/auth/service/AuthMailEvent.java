package com.tanidikvar.api.auth.service;
import com.tanidikvar.api.auth.entity.ActionPurpose;
public record AuthMailEvent(String email, String token, ActionPurpose purpose, boolean mobile) {
    public AuthMailEvent(String email, String token, ActionPurpose purpose) { this(email, token, purpose, false); }
    @Override public String toString() { return "AuthMailEvent[REDACTED]"; }
}
