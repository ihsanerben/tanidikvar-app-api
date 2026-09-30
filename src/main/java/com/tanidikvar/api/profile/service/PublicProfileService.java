package com.tanidikvar.api.profile.service;
import com.tanidikvar.api.profile.repository.PublicProfileRepository;
import com.tanidikvar.api.profile.mapper.PublicProfileMapper;
import com.tanidikvar.api.profile.dto.PublicProfileResponse;
import com.tanidikvar.api.profile.dto.ProfileContributionSummaryResponse;
import com.tanidikvar.api.profile.repository.ProfileContributionSummaryRepository;
import com.tanidikvar.api.common.error.DomainException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PublicProfileService {
 private final PublicProfileRepository profiles;private final PublicProfileMapper mapper;private final ProfileContributionSummaryRepository summaries;
 public PublicProfileService(PublicProfileRepository profiles,PublicProfileMapper mapper,ProfileContributionSummaryRepository summaries){this.profiles=profiles;this.mapper=mapper;this.summaries=summaries;}
 @Transactional(readOnly=true)
 public PublicProfileResponse get(UUID id){return profiles.find(id).map(mapper::toResponse).orElseThrow(()->new DomainException(404,"NOT_FOUND","Profil bulunamadı."));}
 @Transactional(readOnly=true)
 public ProfileContributionSummaryResponse contributionSummary(UUID id){get(id);return summaries.getPublic(id);}
}
