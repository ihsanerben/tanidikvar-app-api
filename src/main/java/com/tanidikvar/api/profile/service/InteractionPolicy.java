package com.tanidikvar.api.profile.service;
import com.tanidikvar.api.common.error.DomainException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class InteractionPolicy {
    private final ProfileSummaryService profiles;
    public InteractionPolicy(ProfileSummaryService profiles) { this.profiles=profiles; }
    @Transactional(readOnly=true)
    public void requireCompleted(UUID id) {
        if (profiles.status(id)==null) throw new DomainException(403,"PROFILE_REQUIRED","Bu işlem için profilini tamamla.");
    }
}
