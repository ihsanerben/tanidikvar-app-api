package com.tanidikvar.api;

import com.tanidikvar.api.auth.service.*;
import com.tanidikvar.api.auth.entity.ActionPurpose;
import com.tanidikvar.api.auth.security.JwtTokens;
import com.tanidikvar.api.auth.repository.AuthSessionRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.context.event.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@ActiveProfiles("local")
@Testcontainers
@RecordApplicationEvents
class AuthenticationIT {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.9-alpine");
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", postgres::getJdbcUrl); p.add("spring.datasource.username", postgres::getUsername);
        p.add("spring.datasource.password", postgres::getPassword);
        p.add("app.auth.secret", () -> Base64.getEncoder().encodeToString(new byte[48]));
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ApplicationEvents events;
    @Autowired JwtTokens tokens;
    @MockitoBean JavaMailSender mailSender;
    @MockitoSpyBean AuthSessionRepository sessions;
    private static final String PASSWORD = "Test-password-123!";
    private static final AtomicInteger addresses = new AtomicInteger();

    MockHttpServletRequestBuilder postJson(String path, Object body) {
        return post(path).with(csrf()).with(request -> { request.setRemoteAddr("test-" + addresses.incrementAndGet()); return request; })
                .contentType("application/json").content(mapper.writeValueAsString(body));
    }
    String email() { return "test-" + UUID.randomUUID() + "@example.test"; }
    void register(String email) throws Exception {
        mvc.perform(postJson("/api/auth/register", Map.of("email", email, "password", PASSWORD)))
                .andExpect(status().isAccepted()).andExpect(cookie().doesNotExist("TV_ACCESS"));
    }
    String action(String email, ActionPurpose purpose) {
        return events.stream(AuthMailEvent.class).filter(e -> e.email().equals(email) && e.purpose() == purpose)
                .reduce((a,b) -> b).orElseThrow().token();
    }
    void verify(String email) throws Exception {
        mvc.perform(postJson("/api/auth/verify-email", Map.of("token", action(email, ActionPurpose.VERIFY_EMAIL))))
                .andExpect(status().isNoContent());
    }
    MvcResult login(String email) throws Exception {
        return mvc.perform(postJson("/api/auth/login", Map.of("email", email, "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn();
    }
    MvcResult account() throws Exception { String email = email(); register(email); verify(email); return login(email); }
    Cookie access(MvcResult result) { return result.getResponse().getCookie("TV_ACCESS"); }
    Cookie refresh(MvcResult result) { return result.getResponse().getCookie("TV_REFRESH"); }

    @Test void registrationRequiresVerificationAndNeverAcceptsClientAuthority() throws Exception {
        String email = email();
        mvc.perform(postJson("/api/auth/register", Map.of("email", email.toUpperCase(Locale.ROOT), "password", PASSWORD, "authority", "MANAGER")))
                .andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT authority FROM users WHERE email=?", String.class, email)).isEqualTo("MEMBER");
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email=?", String.class, email);
        assertThat(hash).startsWith("$2a$12$").doesNotContain(PASSWORD);
        mvc.perform(postJson("/api/auth/login", Map.of("email", email, "password", PASSWORD)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_UNVERIFIED"));
        verify(email);
        var session = login(email);
        assertThat(access(session).isHttpOnly()).isTrue(); assertThat(refresh(session).isHttpOnly()).isTrue();
        assertThat(refresh(session).getPath()).isEqualTo("/api/auth");
        assertThat(session.getResponse().getHeaders("Set-Cookie")).allMatch(value -> value.contains("SameSite=Lax"));
        mvc.perform(get("/api/me").cookie(access(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER")).andExpect(jsonPath("$.profileCompleted").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.accessToken").doesNotExist());
        register(email);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email=?", Integer.class, email)).isEqualTo(1);
    }

    @Test void validationAndUnknownCredentialsHaveSafeResponses() throws Exception {
        mvc.perform(postJson("/api/auth/register", Map.of("email", "invalid", "password", "short")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists()).andExpect(jsonPath("$.fieldErrors.password").exists());
        mvc.perform(postJson("/api/auth/register", Map.of("email", email(), "password", "ş".repeat(40))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.password").exists());
        String email = email(); register(email);
        var unknown = mvc.perform(postJson("/api/auth/login", Map.of("email", email(), "password", PASSWORD))).andExpect(status().isUnauthorized()).andReturn();
        var wrong = mvc.perform(postJson("/api/auth/login", Map.of("email", email, "password", "wrong-password"))).andExpect(status().isUnauthorized()).andReturn();
        assertThat(mapper.readTree(unknown.getResponse().getContentAsString()).get("code"))
                .isEqualTo(mapper.readTree(wrong.getResponse().getContentAsString()).get("code"));
    }

    @Test void rotationRetainsHistoryAndReuseRevokesFamilyAfterCommit() throws Exception {
        var original = account();
        var rotated = mvc.perform(postJson("/api/auth/refresh", Map.of()).cookie(refresh(original)))
                .andExpect(status().isOk()).andReturn();
        assertThat(refresh(rotated).getValue()).isNotEqualTo(refresh(original).getValue());
        assertThat(tokens.read(refresh(rotated).getValue(), "refresh").getExpiresAt())
                .isEqualTo(tokens.read(refresh(original).getValue(), "refresh").getExpiresAt());
        String hash = tokens.hash(refresh(original).getValue());
        assertThat(jdbc.queryForObject("SELECT replaced_by_id IS NOT NULL FROM auth_sessions WHERE token_hash=?", Boolean.class, hash)).isTrue();
        mvc.perform(postJson("/api/auth/refresh", Map.of()).cookie(refresh(original))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(access(rotated))).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/refresh", Map.of()).cookie(refresh(rotated))).andExpect(status().isUnauthorized());
    }

    @Test void parallelRefreshAllowsOneRotationThenRevokesBoth() throws Exception {
        var original = account();
        var latch = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<MvcResult> call = () -> { latch.await(); return mvc.perform(postJson("/api/auth/refresh", Map.of()).cookie(refresh(original))).andReturn(); };
            var first = executor.submit(call); var second = executor.submit(call); latch.countDown();
            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().map(r -> r.getResponse().getStatus())).containsExactlyInAnyOrder(200, 401);
            var winner = results.stream().filter(r -> r.getResponse().getStatus() == 200).findFirst().orElseThrow();
            mvc.perform(get("/api/me").cookie(access(winner))).andExpect(status().isUnauthorized());
        }
    }

    @Test void failedRotationRollsBackRetirement() throws Exception {
        var original = account();
        doThrow(new org.springframework.dao.TransientDataAccessResourceException("test outage")).when(sessions).saveAndFlush(any());
        try { mvc.perform(postJson("/api/auth/refresh", Map.of()).cookie(refresh(original))).andExpect(status().isServiceUnavailable()); }
        finally { reset(sessions); }
        mvc.perform(get("/api/me").cookie(access(original))).andExpect(status().isOk());
        mvc.perform(postJson("/api/auth/refresh", Map.of()).cookie(refresh(original))).andExpect(status().isOk());
    }

    @Test void logoutIsIdempotentAndRevokesAccessImmediately() throws Exception {
        var original = account();
        var logout = mvc.perform(postJson("/api/auth/logout", Map.of()).cookie(refresh(original))).andExpect(status().isNoContent()).andReturn();
        assertThat(logout.getResponse().getCookie("TV_ACCESS").getMaxAge()).isZero();
        assertThat(logout.getResponse().getCookie("TV_REFRESH").getMaxAge()).isZero();
        mvc.perform(get("/api/me").cookie(access(original))).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/refresh", Map.of()).cookie(refresh(original))).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/logout", Map.of())).andExpect(status().isNoContent());
    }

    @Test void logoutRevokesTheFamilyEvenWhenRefreshCookieIsMissing() throws Exception {
        var original = account();
        mvc.perform(postJson("/api/auth/logout", Map.of()).cookie(access(original))).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(access(original))).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/refresh", Map.of()).cookie(refresh(original))).andExpect(status().isUnauthorized());
    }

    @Test void parallelRegistrationPreservesEmailUniqueness() throws Exception {
        String email = email();
        var latch = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> call = () -> { latch.await(); return mvc.perform(postJson("/api/auth/register",
                    Map.of("email", email, "password", PASSWORD))).andReturn().getResponse().getStatus(); };
            var a = executor.submit(call); var b = executor.submit(call); latch.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsOnly(202);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email=?", Integer.class, email)).isEqualTo(1);
    }

    @Test void currentAuthorityAndSoftDeletionOverrideExistingJwt() throws Exception {
        var original = account();
        UUID id = UUID.fromString(tokens.read(access(original).getValue(), "access").getSubject());
        jdbc.update("UPDATE users SET authority='ADMIN' WHERE id=?", id);
        mvc.perform(get("/api/me").cookie(access(original))).andExpect(jsonPath("$.role").value("ADMIN"));
        jdbc.update("UPDATE users SET authority='MEMBER' WHERE id=?", id);
        mvc.perform(get("/api/me").cookie(access(original))).andExpect(jsonPath("$.role").value("USER"));
        jdbc.update("UPDATE users SET deleted_at=CURRENT_TIMESTAMP WHERE id=?", id);
        mvc.perform(get("/api/me").cookie(access(original))).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/refresh", Map.of()).cookie(refresh(original))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test void invalidExpiredAndWrongPurposeTokensCannotAuthenticate() throws Exception {
        var original = account();
        var jwt = tokens.read(access(original).getValue(), "access");
        var pastTokens = new JwtTokens(new com.tanidikvar.api.auth.config.AuthProperties(Base64.getEncoder().encodeToString(new byte[48]),
                java.time.Duration.ofMinutes(15), java.time.Duration.ofDays(14)), java.time.Clock.fixed(Instant.now().minusSeconds(3600), java.time.ZoneOffset.UTC));
        String expired = pastTokens.issue("access", UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("family")), UUID.randomUUID(), Instant.now().minusSeconds(10));
        mvc.perform(get("/api/me").cookie(new Cookie("TV_ACCESS", expired))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(new Cookie("TV_ACCESS", refresh(original).getValue()))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(new Cookie("TV_ACCESS", "invalid.jwt.token"))).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/refresh", Map.of()).cookie(new Cookie("TV_REFRESH", access(original).getValue()))).andExpect(status().isUnauthorized());
    }

    @Test void resetTokenIsHashedSingleUseAndRevokesAllLogins() throws Exception {
        String email = email(); register(email); verify(email);
        var first = login(email); var second = login(email);
        mvc.perform(postJson("/api/auth/forgot-password", Map.of("email", email))).andExpect(status().isAccepted());
        String token = action(email, ActionPurpose.RESET_PASSWORD);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_action_tokens WHERE token_hash=?", Integer.class, tokens.hash(token))).isEqualTo(1);
        mvc.perform(postJson("/api/auth/verify-email", Map.of("token", token))).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/auth/reset-password", Map.of("token", token, "password", "New-password-456!"))).andExpect(status().isNoContent());
        for (var login : List.of(first, second)) {
            mvc.perform(get("/api/me").cookie(access(login))).andExpect(status().isUnauthorized());
            mvc.perform(postJson("/api/auth/refresh", Map.of()).cookie(refresh(login))).andExpect(status().isUnauthorized());
        }
        mvc.perform(postJson("/api/auth/reset-password", Map.of("token", token, "password", PASSWORD))).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/auth/login", Map.of("email", email, "password", PASSWORD))).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/login", Map.of("email", email, "password", "New-password-456!"))).andExpect(status().isOk());
        mvc.perform(postJson("/api/auth/forgot-password", Map.of("email", email()))).andExpect(status().isAccepted()).andExpect(content().string(""));
    }

    @Test void verificationCannotBeReplayedAndExpiredLinksFail() throws Exception {
        String email = email(); register(email);
        String token = action(email, ActionPurpose.VERIFY_EMAIL);
        verify(email);
        mvc.perform(postJson("/api/auth/verify-email", Map.of("token", token))).andExpect(status().isBadRequest());
        String another = email(); register(another);
        String expired = action(another, ActionPurpose.VERIFY_EMAIL);
        jdbc.update("UPDATE auth_action_tokens SET created_at=CURRENT_TIMESTAMP-interval '2 days', expires_at=CURRENT_TIMESTAMP-interval '1 day' WHERE token_hash=?", tokens.hash(expired));
        mvc.perform(postJson("/api/auth/verify-email", Map.of("token", expired))).andExpect(status().isBadRequest());
    }

    @Test void parallelTokenRedemptionSucceedsOnlyOnce() throws Exception {
        String email = email(); register(email);
        String token = action(email, ActionPurpose.VERIFY_EMAIL);
        var latch = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> call = () -> { latch.await(); return mvc.perform(postJson("/api/auth/verify-email", Map.of("token", token))).andReturn().getResponse().getStatus(); };
            var a = executor.submit(call); var b = executor.submit(call); latch.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(204, 400);
        }
    }

    @Test void rateLimitIgnoresSpoofedForwardedAddresses() throws Exception {
        for (int i = 0; i < 10; i++) mvc.perform(postJson("/api/auth/forgot-password", Map.of("email", email()))
                .with(r -> { r.setRemoteAddr("rate-test"); return r; })).andExpect(status().isAccepted());
        mvc.perform(postJson("/api/auth/forgot-password", Map.of("email", email())).header("X-Forwarded-For", "192.0.2.99")
                        .with(r -> { r.setRemoteAddr("rate-test"); return r; }))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test void authenticationTablesRejectPhysicalDeletion() {
        for (String table : List.of("users", "auth_sessions", "auth_action_tokens")) {
            assertThatThrownBy(() -> jdbc.execute("DELETE FROM " + table)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE users, auth_sessions, auth_action_tokens, user_profiles, tags, management_actions, questions, question_tags"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
