package com.tanidikvar.api.profile.service;
import com.tanidikvar.api.profile.repository.PublicProfileRepository;
import com.tanidikvar.api.profile.mapper.PublicProfileMapper;
import com.tanidikvar.api.profile.dto.PublicProfileResponse;
import com.tanidikvar.api.common.error.DomainException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PublicProfileService {
 private final PublicProfileRepository profiles;private final PublicProfileMapper mapper;
 public PublicProfileService(PublicProfileRepository profiles,PublicProfileMapper mapper){this.profiles=profiles;this.mapper=mapper;}
 @Transactional(readOnly=true)
 public PublicProfileResponse get(UUID id){return profiles.find(id).map(mapper::toResponse).orElseThrow(()->new DomainException(404,"NOT_FOUND","Profil bulunamadı."));}
}
