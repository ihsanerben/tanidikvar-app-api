package com.tanidikvar.api.profile.service;
import com.tanidikvar.api.profile.repository.ProfileRepository;
import com.tanidikvar.api.profile.entity.EducationStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ProfileSummaryService {
    private final ProfileRepository profiles;
    public ProfileSummaryService(ProfileRepository profiles) { this.profiles=profiles; }
    @Transactional(readOnly=true)
    public EducationStatus status(UUID id) { return profiles.find(id).filter(p -> p.deletedAt()==null).map(p -> p.educationStatus()).orElse(null); }
}
