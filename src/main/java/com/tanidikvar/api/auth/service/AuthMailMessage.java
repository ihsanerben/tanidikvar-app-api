package com.tanidikvar.api.auth.service;

public record AuthMailMessage(String from, String to, String subject, String text, String html) {}
