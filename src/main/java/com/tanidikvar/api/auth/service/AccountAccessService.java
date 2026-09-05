package com.tanidikvar.api.auth.service;

import com.tanidikvar.api.auth.entity.*;
import com.tanidikvar.api.auth.exception.AuthRejectedException;
import com.tanidikvar.api.auth.repository.AccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AccountAccessService {
    private final AccountRepository accounts;
    public AccountAccessService(AccountRepository accounts) { this.accounts=accounts; }
    @Transactional(propagation=Propagation.MANDATORY)
    public Account lockActive(UUID id) {
        return accounts.lockById(id).filter(a -> !a.isDeleted() && a.isEmailVerified()).orElseThrow(AuthRejectedException::new);
    }
}
