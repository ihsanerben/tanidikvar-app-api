package com.tanidikvar.api.auth.dto;

import java.util.UUID;

public record CurrentUserResponse(UUID id, String email, String role, boolean profileCompleted) { }
