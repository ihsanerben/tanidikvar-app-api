package com.tanidikvar.api.file.dto;
public record PreparedUpload(byte[] bytes, String contentType, String sha256) {}

