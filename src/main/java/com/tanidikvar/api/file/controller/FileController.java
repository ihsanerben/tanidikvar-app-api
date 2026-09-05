package com.tanidikvar.api.file.controller;
import com.tanidikvar.api.file.dto.*;
import com.tanidikvar.api.file.service.*;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController
public class FileController {
 private final FileService files;private final AvatarUploadService avatars;
 public FileController(FileService files,AvatarUploadService avatars){this.files=files;this.avatars=avatars;}
 @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="accessCookie")
 @GetMapping("/api/me/avatar") public AvatarResponse info(@AuthenticationPrincipal SessionPrincipal p){return files.avatarInfo(p.userId());}
 @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="accessCookie")
 @PostMapping(value="/api/me/avatar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public AvatarResponse upload(@AuthenticationPrincipal SessionPrincipal p,@RequestPart("file") MultipartFile file){return avatars.upload(p.userId(),file);}
 @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="accessCookie")
 @PostMapping("/api/me/avatar/remove") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@AuthenticationPrincipal SessionPrincipal p){files.removeAvatar(p.userId());}
 @GetMapping("/api/avatars/{id}") public ResponseEntity<byte[]> avatar(@PathVariable UUID id){return response(files.avatarDownload(id),false);}
 @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="accessCookie")
 @GetMapping("/api/files/{id}/download") public ResponseEntity<byte[]> document(@PathVariable UUID id,@AuthenticationPrincipal SessionPrincipal p){return response(files.document(id,p.userId()),true);}
 private ResponseEntity<byte[]> response(FileDownload file,boolean attachment){return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; default-src 'none'").header("Content-Disposition",(attachment?"attachment":"inline")+"; filename=\""+file.filename()+"\"").body(file.bytes());}
}

