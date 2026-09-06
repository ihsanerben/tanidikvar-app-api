package com.tanidikvar.api.profile.controller;
import com.tanidikvar.api.profile.dto.PublicProfileResponse;
import com.tanidikvar.api.profile.service.PublicProfileService;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/profiles")
public class PublicProfileController {
 private final PublicProfileService profiles;
 public PublicProfileController(PublicProfileService profiles){this.profiles=profiles;}
 @GetMapping("/{id}") public PublicProfileResponse get(@PathVariable UUID id){return profiles.get(id);}
}
