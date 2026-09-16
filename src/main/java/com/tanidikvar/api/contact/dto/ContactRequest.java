package com.tanidikvar.api.contact.dto;
import jakarta.validation.constraints.Email;import jakarta.validation.constraints.NotBlank;import jakarta.validation.constraints.Size;
public record ContactRequest(@NotBlank @Size(max=120) String name,@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=160) String subject,@NotBlank @Size(min=10,max=5000) String message) {}
