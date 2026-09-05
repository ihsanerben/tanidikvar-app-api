package com.tanidikvar.api.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ResponseEntity<ApiError> database(Exception error, HttpServletRequest request) {
        log.error("database_unavailable requestId={} errorType={}", request.getAttribute("requestId"), error.getClass().getSimpleName());
        return ResponseEntity.status(503).body(ApiErrors.create(request, 503, "SERVICE_UNAVAILABLE", "Hizmete şu anda ulaşılamıyor."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        var fields = new LinkedHashMap<String, String>();
        error.getBindingResult().getFieldErrors().forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError(Instant.now(), 400, "VALIDATION_FAILED",
                "Lütfen form alanlarını kontrol et.", request.getRequestURI(),
                (String) request.getAttribute("requestId"), fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiErrors.create(request, 400, "INVALID_REQUEST", "İstek okunamadı."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> missing(HttpServletRequest request) {
        return ResponseEntity.status(404).body(ApiErrors.create(request, 404, "NOT_FOUND", "İçerik bulunamadı."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception error, HttpServletRequest request) {
        log.error("request_failed requestId={} errorType={}", request.getAttribute("requestId"), error.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(ApiErrors.create(request, 500, "INTERNAL_ERROR", "İşlem tamamlanamadı."));
    }
}
