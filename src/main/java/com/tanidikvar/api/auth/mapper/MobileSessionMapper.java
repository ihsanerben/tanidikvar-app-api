package com.tanidikvar.api.auth.mapper;

import com.tanidikvar.api.auth.dto.MobileSessionResponse;
import com.tanidikvar.api.auth.service.IssuedSession;
import org.springframework.stereotype.Component;

@Component
public class MobileSessionMapper {
    public MobileSessionResponse toResponse(IssuedSession session) {
        return new MobileSessionResponse(session.user(), session.accessToken(), session.refreshToken(),
                session.accessExpiresAt(), session.refreshExpiresAt());
    }
}
