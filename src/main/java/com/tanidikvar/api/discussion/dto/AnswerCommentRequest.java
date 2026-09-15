package com.tanidikvar.api.discussion.dto;import jakarta.validation.constraints.*;public record AnswerCommentRequest(@NotBlank@Size(min=2,max=2000)String body){}
