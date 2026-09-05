package com.tanidikvar.api.auth.security;

import com.tanidikvar.api.auth.dto.CurrentUserResponse;
import java.util.UUID;

public record SessionPrincipal(UUID userId, UUID familyId, CurrentUserResponse user) { }
