package com.tanidikvar.api.profile.service;
import com.tanidikvar.api.profile.repository.ProfileRepository;
import com.tanidikvar.api.profile.entity.EducationStatus;
import com.tanidikvar.api.file.repository.FileRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ProfileSummaryService {
    private final ProfileRepository profiles;
    private final FileRepository files;
    public ProfileSummaryService(ProfileRepository profiles,FileRepository files) { this.profiles=profiles; this.files=files; }
    @Transactional(readOnly=true)
    public EducationStatus status(UUID id) { return profiles.find(id).filter(p -> p.deletedAt()==null).map(p -> p.educationStatus()).orElse(null); }
    @Transactional(readOnly=true)
    public boolean completed(UUID id) { return status(id)!=null && files.avatar(id).isPresent(); }
}
