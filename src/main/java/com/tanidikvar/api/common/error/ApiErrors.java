package com.tanidikvar.api.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;

public final class ApiErrors {
    private ApiErrors() {}
    public static ApiError create(HttpServletRequest request, int status, String code, String message) {
        return new ApiError(Instant.now(), status, code, message, request.getRequestURI(),
                (String) request.getAttribute("requestId"), Map.of());
    }
}
