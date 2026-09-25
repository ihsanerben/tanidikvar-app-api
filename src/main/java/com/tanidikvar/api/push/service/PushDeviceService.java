package com.tanidikvar.api.push.service;

import com.tanidikvar.api.auth.repository.AccountRepository;
import com.tanidikvar.api.auth.repository.AuthSessionRepository;
import com.tanidikvar.api.auth.exception.AuthRejectedException;
import com.tanidikvar.api.auth.security.SessionPrincipal;
import com.tanidikvar.api.push.dto.PushDeviceRequest;
import com.tanidikvar.api.push.repository.PushDeviceRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushDeviceService {
    private final PushDeviceRepository devices;
    private final AccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final Clock clock;
    public PushDeviceService(PushDeviceRepository devices, AccountRepository accounts, AuthSessionRepository sessions, Clock clock) {
        this.devices=devices; this.accounts=accounts; this.sessions=sessions; this.clock=clock;
    }
    @Transactional public void register(SessionPrincipal principal, PushDeviceRequest request) {
        // Same account lock as logout/rotation: a late request cannot restore a revoked registration.
        var account=accounts.lockById(principal.userId()).orElseThrow(AuthRejectedException::new);
        if(account.isDeleted() || !sessions.existsByUserIdAndFamilyIdAndReplacedByIdIsNullAndRevokedAtIsNullAndDeletedAtIsNullAndExpiresAtAfter(
                principal.userId(),principal.familyId(),clock.instant())) throw new AuthRejectedException();
        devices.register(principal.userId(),principal.familyId(),request.pushToken(),request.platform());
    }
    @Transactional public void unregister(SessionPrincipal principal) { devices.unregister(principal.userId(),principal.familyId()); }
}
