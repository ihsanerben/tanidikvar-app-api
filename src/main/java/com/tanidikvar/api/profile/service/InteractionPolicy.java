package com.tanidikvar.api.profile.service;
import com.tanidikvar.api.common.error.DomainException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class InteractionPolicy {
    private final ProfileSummaryService profiles;
    private final com.tanidikvar.api.auth.repository.AccountRepository accounts;
    public InteractionPolicy(ProfileSummaryService profiles, com.tanidikvar.api.auth.repository.AccountRepository accounts) { this.profiles=profiles; this.accounts=accounts; }
    @Transactional(readOnly=true)
    public void requireCompleted(UUID id) {
        requireParticipant(id);
        if (!profiles.completed(id)) throw new DomainException(403,"PROFILE_REQUIRED","Bu işlem için profilini tamamla.");
    }
    @Transactional(readOnly=true)
    public void requireParticipant(UUID id) {
        if (accounts.findById(id).map(a -> a.getAuthority()==com.tanidikvar.api.auth.entity.Authority.MANAGER).orElse(false))
            throw new DomainException(403,"MANAGER_PARTICIPATION_FORBIDDEN","Manager hesabı yalnız yönetim işlemleri yapabilir.");
    }
}
