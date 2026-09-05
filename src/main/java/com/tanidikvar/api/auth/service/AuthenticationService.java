package com.tanidikvar.api.auth.service;

import com.tanidikvar.api.auth.config.AuthProperties;
import com.tanidikvar.api.auth.entity.*;
import com.tanidikvar.api.auth.exception.*;
import com.tanidikvar.api.auth.mapper.AccountMapper;
import com.tanidikvar.api.auth.repository.*;
import com.tanidikvar.api.auth.security.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {
    private final AccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final JwtTokens tokens;
    private final AccountMapper mapper;
    private final AuthProperties properties;
    private final Clock clock;
    private final String dummyHash;

    public AuthenticationService(AccountRepository accounts, AuthSessionRepository sessions, PasswordEncoder passwords,
            JwtTokens tokens, AccountMapper mapper, AuthProperties properties, Clock clock) {
        this.accounts = accounts; this.sessions = sessions; this.passwords = passwords;
        this.tokens = tokens; this.mapper = mapper; this.properties = properties; this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public static String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    public static void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw new PasswordFormatException();
    }

    @Transactional
    public IssuedSession login(String email, String password) {
        validatePassword(password);
        var candidate = accounts.findIdByEmail(normalizeEmail(email));
        var locked = candidate.flatMap(accounts::lockById);
        boolean matches = passwords.matches(password, locked.map(Account::getPasswordHash).orElse(dummyHash));
        if (!matches || locked.isEmpty()) throw new AuthRejectedException();
        var account = locked.get();
        if (account.isDeleted()) throw new AuthRejectedException();
        if (!account.isEmailVerified()) throw new EmailUnverifiedException();
        return issue(account, UUID.randomUUID(), clock.instant().plus(properties.refreshTtl()));
    }

    // Expected rejection commits family revocation. Unexpected errors still roll back the whole rotation.
    @Transactional(noRollbackFor = AuthRejectedException.class)
    public IssuedSession refresh(String refreshToken) {
        var jwt = tokens.read(refreshToken, "refresh");
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID familyId = UUID.fromString(jwt.getClaimAsString("family"));
        var account = accounts.lockById(userId).orElseThrow(AuthRejectedException::new);
        var old = sessions.findById(UUID.fromString(jwt.getId())).orElseThrow(AuthRejectedException::new);
        if (!old.getUserId().equals(userId) || !old.getFamilyId().equals(familyId)
                || !old.getTokenHash().equals(tokens.hash(refreshToken))) throw new AuthRejectedException();
        if (account.isDeleted() || !old.isActive(clock.instant())) {
            sessions.revokeFamily(userId, familyId, clock.instant());
            throw new AuthRejectedException();
        }
        // Release the active-family unique slot before inserting the replacement. Both writes commit together.
        old.retire(clock.instant()); sessions.flush();
        var result = issue(account, familyId, old.getExpiresAt());
        old.replaceWith(UUID.fromString(tokens.read(result.refreshToken(), "refresh").getId()), clock.instant());
        return result;
    }

    @Transactional
    public void logout(String refreshToken, String accessToken) {
        try {
            org.springframework.security.oauth2.jwt.Jwt jwt;
            try { jwt = tokens.read(refreshToken, "refresh"); }
            catch (AuthRejectedException ignored) { jwt = tokens.read(accessToken, "access"); }
            UUID userId = UUID.fromString(jwt.getSubject());
            if (accounts.lockById(userId).isPresent())
                sessions.revokeFamily(userId, UUID.fromString(jwt.getClaimAsString("family")), clock.instant());
        } catch (AuthRejectedException ignored) { /* Logout is idempotent, including missing/expired cookies. */ }
    }

    @Transactional(readOnly = true)
    public SessionPrincipal authenticate(String accessToken) {
        var jwt = tokens.read(accessToken, "access");
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID familyId = UUID.fromString(jwt.getClaimAsString("family"));
        var account = accounts.findById(userId).filter(a -> !a.isDeleted() && a.isEmailVerified()).orElseThrow(AuthRejectedException::new);
        if (!sessions.existsByUserIdAndFamilyIdAndReplacedByIdIsNullAndRevokedAtIsNullAndDeletedAtIsNullAndExpiresAtAfter(userId, familyId, clock.instant()))
            throw new AuthRejectedException();
        return new SessionPrincipal(userId, familyId, mapper.toResponse(account));
    }

    private IssuedSession issue(Account account, UUID familyId, Instant refreshExpiry) {
        Instant now = clock.instant();
        UUID refreshId = UUID.randomUUID();
        Instant accessExpiry = now.plus(properties.accessTtl());
        if (accessExpiry.isAfter(refreshExpiry)) accessExpiry = refreshExpiry;
        String refresh = tokens.issue("refresh", account.getId(), familyId, refreshId, refreshExpiry);
        String access = tokens.issue("access", account.getId(), familyId, UUID.randomUUID(), accessExpiry);
        sessions.saveAndFlush(new AuthSession(refreshId, account.getId(), familyId, tokens.hash(refresh), refreshExpiry, now));
        return new IssuedSession(mapper.toResponse(account), access, refresh, accessExpiry, refreshExpiry);
    }
}
