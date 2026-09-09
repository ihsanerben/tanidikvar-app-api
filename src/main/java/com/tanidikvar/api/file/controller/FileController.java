package com.tanidikvar.api.file.controller;
import com.tanidikvar.api.file.dto.FileDownload;
import com.tanidikvar.api.file.service.FileService;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class FileController {
 private final FileService files;
 public FileController(FileService files){this.files=files;}
 @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="accessCookie")
 @GetMapping("/api/files/{id}/download") public ResponseEntity<byte[]> document(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal p){return response(files.document(id,p.userId()),true);}
 private ResponseEntity<byte[]> response(FileDownload file,boolean attachment){return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; default-src 'none'").header("Content-Disposition",(attachment?"attachment":"inline")+"; filename=\""+file.filename()+"\"").body(file.bytes());}
}
