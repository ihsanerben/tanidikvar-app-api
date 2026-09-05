package com.tanidikvar.api.auth.service;

import com.tanidikvar.api.auth.entity.*;
import com.tanidikvar.api.auth.exception.*;
import com.tanidikvar.api.auth.repository.*;
import com.tanidikvar.api.auth.security.JwtTokens;
import java.security.SecureRandom;
import java.time.*;
import java.util.Base64;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountActionService {
    private final AccountRepository accounts;
    private final ActionTokenRepository actions;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final JwtTokens tokens;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public AccountActionService(AccountRepository accounts, ActionTokenRepository actions, AuthSessionRepository sessions,
            PasswordEncoder passwords, JwtTokens tokens, Clock clock, ApplicationEventPublisher events) {
        this.accounts = accounts; this.actions = actions; this.sessions = sessions; this.passwords = passwords;
        this.tokens = tokens; this.clock = clock; this.events = events;
    }
    @Transactional
    public void register(String email, String password) {
        AuthenticationService.validatePassword(password);
        String hash = passwords.encode(password);
        // Same HTTP result for existing and new addresses; no account enumeration.
        var id = java.util.UUID.randomUUID();
        if (accounts.insertIfAbsent(id, AuthenticationService.normalizeEmail(email), hash, clock.instant()) == 0) return;
        var account = accounts.findById(id).orElseThrow(RegistrationException::new);
        send(account, ActionPurpose.VERIFY_EMAIL);
    }
    @Transactional
    public void request(String email, ActionPurpose purpose) {
        var candidate = accounts.findIdByEmail(AuthenticationService.normalizeEmail(email));
        if (candidate.isEmpty()) return;
        var account = accounts.lockById(candidate.get()).orElseThrow(AuthRejectedException::new);
        if (account.isDeleted() || (purpose == ActionPurpose.VERIFY_EMAIL && account.isEmailVerified())
                || (purpose == ActionPurpose.RESET_PASSWORD && !account.isEmailVerified())) return;
        send(account, purpose);
    }
    @Transactional
    public void verify(String token) {
        Account account = redeem(token, ActionPurpose.VERIFY_EMAIL);
        account.verifyEmail(clock.instant());
    }
    @Transactional
    public void resetPassword(String token, String password) {
        AuthenticationService.validatePassword(password);
        Account account = redeem(token, ActionPurpose.RESET_PASSWORD);
        account.changePassword(passwords.encode(password), clock.instant());
        sessions.revokeAll(account.getId(), clock.instant());
    }
    private Account redeem(String token, ActionPurpose purpose) {
        String hash = tokens.hash(token);
        var userId = actions.findUserId(hash).orElseThrow(ActionTokenException::new);
        var account = accounts.lockById(userId).orElseThrow(ActionTokenException::new);
        var action = actions.findByTokenHash(hash).orElseThrow(ActionTokenException::new);
        if (account.isDeleted() || !action.usable(purpose, clock.instant())) throw new ActionTokenException();
        actions.consumeAll(userId, purpose, clock.instant());
        return account;
    }
    private void send(Account account, ActionPurpose purpose) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Duration ttl = purpose == ActionPurpose.VERIFY_EMAIL ? Duration.ofHours(24) : Duration.ofMinutes(30);
        actions.save(new ActionToken(account.getId(), purpose, tokens.hash(token), clock.instant().plus(ttl), clock.instant()));
        events.publishEvent(new AuthMailEvent(account.getEmail(), token, purpose));
    }
}
