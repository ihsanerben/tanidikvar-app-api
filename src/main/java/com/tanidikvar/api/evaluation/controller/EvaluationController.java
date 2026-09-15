package com.tanidikvar.api.evaluation.controller;
import com.tanidikvar.api.auth.security.SessionPrincipal;import com.tanidikvar.api.common.dto.PageResponse;import com.tanidikvar.api.evaluation.dto.*;import com.tanidikvar.api.evaluation.service.EvaluationService;import jakarta.validation.Valid;import java.util.UUID;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/evaluations") public class EvaluationController{
 private final EvaluationService service;public EvaluationController(EvaluationService service){this.service=service;}
 @PutMapping public EvaluationResponse save(@AuthenticationPrincipal SessionPrincipal user,@Valid @RequestBody EvaluationRequest request){return service.save(user.userId(),request);}
 @GetMapping public PageResponse<EvaluationResponse> list(@RequestParam UUID universityId,@RequestParam(required=false) UUID programId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(universityId,programId,page,size);}
 @GetMapping("/summary") public EvaluationSummaryResponse summary(@RequestParam UUID universityId,@RequestParam(required=false) UUID programId){return service.summary(universityId,programId);}
}
