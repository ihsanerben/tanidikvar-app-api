package com.tanidikvar.api.answer.controller;
import com.tanidikvar.api.answer.dto.AnswerResponse;
import com.tanidikvar.api.answer.service.AnswerDetailService;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class AnswerDetailController {
 private final AnswerDetailService service;
 public AnswerDetailController(AnswerDetailService service){this.service=service;}
 @GetMapping("/api/answers/{id}") public AnswerResponse get(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal principal){return service.get(id,principal==null?null:principal.userId());}
}
