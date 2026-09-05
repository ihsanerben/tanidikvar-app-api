package com.tanidikvar.api.answer.dto;
import java.time.*;
public record AdminQuotaResponse(boolean activeAdmin,LocalDate day,long used,int limit,long remaining,Instant resetsAt) {}

